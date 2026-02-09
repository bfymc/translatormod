# translatormod

This mod can translate english text you write to chinese. It also translates chinese messages from chat into english.

## Prerequisites

This mod requires:
* A [DeepL](https://www.deepl.com/) API key (free)
* The [Fabric API mod](https://modrinth.com/mod/fabric-api) to be installed
* Minecraft version 1.21.11 (for now, at least)

## Configuration

You have to place the API key you have acquired from DeepL into a JSON file in the .minecraft/config folder. This JSON file should be called `translatormod.json`. You can also automatically create this file by simply starting Minecraft with this mod installed. A correct configuration should look something like this:

```json
{
  "apiKey": "e4f4893d-ce2...58a1243b:??"
}
```

You may also set a key in-game using the command `/setdeeplkey <key>`.

## Usage

If a valid key has been set, chat messages containing chinese text should now have a corresponding chat message in english.

To translate english text to chinese, use the `/tr <message>` command.

## Credits

Made by Bunzo & Bfy.