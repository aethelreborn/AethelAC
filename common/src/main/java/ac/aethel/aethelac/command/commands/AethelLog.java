package ac.aethel.aethelac.command.commands;

import ac.aethel.aethelac.AethelAPI;
import ac.aethel.aethelac.command.BuildableCommand;
import ac.aethel.aethelac.manager.init.start.SuperDebug;
import ac.aethel.aethelac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.aethel.aethelac.platform.api.sender.Sender;
import ac.aethel.aethelac.utils.anticheat.LogUtil;
import ac.aethel.aethelac.utils.anticheat.MessageUtil;
import ac.aethel.aethelac.utils.common.arguments.CommonAethelArguments;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class AethelLog implements BuildableCommand {
    public static void sendLogAsync(Sender sender, String log, Consumer<String> consumer, String type) {
        String success = AethelAPI.INSTANCE.getConfigManager().getConfig().getStringElse("upload-log", "%prefix% &fUploaded debug to: %url%");
        String failure = AethelAPI.INSTANCE.getConfigManager().getConfig().getStringElse("upload-log-upload-failure", "%prefix% &cSomething went wrong while uploading this log, see console for more information.");
        String uploading = AethelAPI.INSTANCE.getConfigManager().getConfig().getStringElse("upload-log-start", "%prefix% &fUploading log... please wait");
        uploading = MessageUtil.replacePlaceholders(sender, uploading);
        sender.sendMessage(MessageUtil.miniMessage(uploading));
        AethelAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(AethelAPI.INSTANCE.getGrimPlugin(), () -> {
            try {
                sendLog(sender, log, success, failure, consumer, type);
            } catch (Exception e) {
                String message = MessageUtil.replacePlaceholders(sender, failure);
                sender.sendMessage(MessageUtil.miniMessage(message));
                LogUtil.error("Failed to send log", e);
            }
        });
    }

    private static void sendLog(Sender sender, String log, String success, String failure, Consumer<String> consumer, String type) throws IOException {
        URL mUrl = new URL(CommonAethelArguments.PASTE_URL.value() + "data/post");
        HttpURLConnection urlConn = (HttpURLConnection) mUrl.openConnection();
        try {
            urlConn.setDoOutput(true);
            urlConn.setRequestMethod("POST");
            urlConn.setConnectTimeout(CommonAethelArguments.URL_TIMEOUT.value());
            urlConn.setReadTimeout(CommonAethelArguments.URL_TIMEOUT.value());
            urlConn.addRequestProperty("User-Agent", "AethelAC/" + AethelAPI.INSTANCE.getExternalAPI().getGrimVersion());
            urlConn.addRequestProperty("Content-Type", type); // Not really yaml, but looks nicer than plaintext
            urlConn.setRequestProperty("Content-Length", Integer.toString(log.length()));
            try (OutputStream stream = urlConn.getOutputStream()) {
                stream.write(log.getBytes(StandardCharsets.UTF_8));
            }
            final int response = urlConn.getResponseCode();
            if (response == HttpURLConnection.HTTP_CREATED) {
                String responseURL = urlConn.getHeaderField("Location");
                String message = success.replace("%url%", CommonAethelArguments.PASTE_URL.value() + responseURL);
                consumer.accept(message);
                message = MessageUtil.replacePlaceholders(sender, message);
                sender.sendMessage(MessageUtil.miniMessage(message));
            } else {
                String message = MessageUtil.replacePlaceholders(sender, failure);
                sender.sendMessage(MessageUtil.miniMessage(message));
                LogUtil.error("Returned response code " + response + ": " + urlConn.getResponseMessage());
            }
        } finally {
            urlConn.disconnect();
        }
    }

    @Override
    public void register(CommandManager<Sender> commandManager, CloudPlatformCommandArguments arguments) {
        Command<Sender> command = commandManager.commandBuilder("aethel", "aethelac")
                .literal("log", "logs")
                .permission("aethel.log")
                .required("flagId", IntegerParser.integerParser())
                .handler(this::handleLog)
                .manager(commandManager)
                .build();
        commandManager
                .command(command)
                .command(commandManager.commandBuilder("gl").proxies(command));
    }

    private void handleLog(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        int flagId = context.get("flagId");

        StringBuilder builder = SuperDebug.getFlag(flagId);
        if (builder == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "upload-log-not-found", "%prefix% &cUnable to find that log"));
            return;
        }
        sendLogAsync(sender, builder.toString(), string -> {}, "text/yaml");
    }
}
