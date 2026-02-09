package com.example.translatemod.mixin.client;

import com.example.translatemod.TranslatorModClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ExampleClientMixin {
	private static final String MOD_ID = "translatormod";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Inject(method = "onGameMessage", at = @At("HEAD"))
	private void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
		LOGGER.info("Mixin (game message): {}", packet.content());
		TranslatorModClient.onChatMessage(packet.content().toString());
	}
}