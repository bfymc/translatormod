package com.example.translatemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class TranslatorModClient implements ClientModInitializer {
	private static String API_KEY = "";
	private static final String API_URL = "https://api-free.deepl.com/v2/translate";
	private static final Pattern LITERAL_PATTERN =
			Pattern.compile("literal\\{([^}]*)}");
	private static final Pattern CHINESE_PATTERN =
			Pattern.compile(".*[\\u4e00-\\u9fa5].*");
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	// Store messages we just sent to prevent translating them back (Loop Prevention)
	private static final Set<String> recentlySentMessages = new HashSet<>();
	private static int tickCounter = 0;

	public static final String MOD_ID = "translatormod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {

		ClientReceiveMessageEvents.CHAT.register((message, signed_message, sender, params, timestamp) -> {
			LOGGER.info("CHAT Message from {}: {} with params {}", sender, message, params);
			onChatMessage(message.toString());
		});

		// Register a tick event to clean up the message list occasionally
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			tickCounter++;
			// Clear the list every 100 ticks (approx 5 seconds) to prevent memory leaks
			if (tickCounter >= 100) {
				recentlySentMessages.clear();
				tickCounter = 0;
			}
		});

		// 1. Register Commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// Command: /tr <text> (English to Chinese - Sends to Chat)
			dispatcher.register(ClientCommandManager.literal("tr")
					.then(ClientCommandManager.argument("text", greedyString())
							.executes(context -> {
								String text = getString(context, "text");
								MinecraftClient client = MinecraftClient.getInstance();

								if (API_KEY.isEmpty()) {
									context.getSource().sendFeedback(Text.literal("Error: API Key not set. Use /setdeeplkey <key>").formatted(Formatting.RED));
									return 1;
								}

								context.getSource().sendFeedback(Text.literal("Translating and sending...").formatted(Formatting.GRAY));

								translateAsync(text, "ZH", translated -> {
									// 1. Add to ignore list so we don't translate our own message back
									recentlySentMessages.add(translated);

									// 2. Send to server so everyone sees it
									if (client.player != null && client.getNetworkHandler() != null) {
										client.player.networkHandler.sendChatMessage(translated);
									}
								});

								return 1;
							})
					)
			);

			// Command: /setdeeplkey <key>
			dispatcher.register(ClientCommandManager.literal("setdeeplkey")
					.then(ClientCommandManager.argument("key", greedyString())
							.executes(context -> {
                                API_KEY = getString(context, "key");
								context.getSource().sendFeedback(Text.literal("DeepL API Key set!").formatted(Formatting.GREEN));
								return 1;
							})
					)
			);
		});
	}

	// Called by ClientPlayNetworkHandlerMixin
	public static void onChatMessage(String content) {
		if (API_KEY.isEmpty()) {
			LOGGER.info("TranslatorModClient: API KEY IS EMPTY");
			return;
		}

		// LOOP PREVENTION: If this message is one we just sent, ignore it.
		if (recentlySentMessages.contains(content)) {
			recentlySentMessages.remove(content); // Remove it so we can translate it next time if needed
			return;
		}

		var listOfChinese = extractChineseLiterals(content);
		LOGGER.info("TranslatorModClient: Found {} matches", listOfChinese.size());

		for (var textSnippet : listOfChinese) {
			LOGGER.info("TranslatorModClient: Translating '{}'", textSnippet);

			translateAsync(textSnippet, "EN-US", translated -> {
				MinecraftClient client = MinecraftClient.getInstance();

				if (client.player != null) {
					// We are already on the main thread here thanks to execute()
					// This message is only seen by the local player
					client.player.sendMessage(
							Text.literal("[Trans] " + translated).formatted(Formatting.GREEN),
							false
					);
				}
			});
		}
	}

	private static void translateAsync(String text, String targetLang, TranslationCallback callback) {
		CompletableFuture.runAsync(() -> {
			try {
				String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
				String formBody = "text=" + encodedText + "&target_lang=" + targetLang;

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL))
						.header("Content-Type", "application/x-www-form-urlencoded")
						.header("Authorization", "DeepL-Auth-Key " + API_KEY)
						.POST(HttpRequest.BodyPublishers.ofString(formBody))
						.build();

				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
					String translatedText = json.getAsJsonArray("translations")
							.get(0).getAsJsonObject()
							.get("text").getAsString();

					// FIX: Run the callback on the Main Thread so it renders in Chat
					MinecraftClient.getInstance().execute(() -> callback.onSuccess(translatedText));

				} else {
					System.err.println("[ChatTranslator] DeepL API Error (" + response.statusCode() + "): " + response.body());

					// FIX: Run error on Main Thread too
					String errorMsg = "API Error " + response.statusCode() + " (Check Log)";
					MinecraftClient.getInstance().execute(() -> callback.onError(errorMsg));
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				String errorMsg = "Translation Failed: " + e.getMessage();
				MinecraftClient.getInstance().execute(() -> callback.onError(errorMsg));
			}
		});
	}

	public static List<String> extractChineseLiterals(String input) {
		List<String> result = new ArrayList<>();

		Matcher matcher = LITERAL_PATTERN.matcher(input);
		while (matcher.find()) {
			String content = matcher.group(1);

			if (CHINESE_PATTERN.matcher(content).matches()) {
				result.add(content);
			}
		}

		return result;
	}

	@FunctionalInterface
	interface TranslationCallback {
		void onSuccess(String translated);
		default void onError(String error) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				client.player.sendMessage(Text.literal(error).formatted(Formatting.RED), false);
			}
		}
	}
}