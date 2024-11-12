package me.waitlists.amyseeker.mixin;

import me.waitlists.amyseeker.Requests;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    private ButtonWidget amySeekerButton;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("RETURN"), method = "initWidgetsNormal")
    private void addAmySeekerButton(int y, int spacingY, CallbackInfo ci) {
        // Create the button
        amySeekerButton = ButtonWidget.builder(Text.of("amyseeker"), (buttonWidget) -> {
                    String url = "https://hzqp.xyz/servers.txt";
                    System.out.println("Requesting server data from: " + url);

                    // Fetch servers from the specified URL
                    fetchServerData(url);
                }).position(this.width / 2 - 100, this.height / 4 + 48 + 72 + 12 + spacingY)
                .size(200, 20)
                .build();

        // Add the button to the screen
        this.addDrawableChild(amySeekerButton);
    }

    private void fetchServerData(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                // Read the response
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine).append("\n");
                }
                in.close();

                processResponse(response.toString());
            } catch (Exception e) {
                System.out.println("Failed to fetch server data: " + e.getMessage());
                updateButtonText("amyseeker | error");
            }
        }).start();
    }

    private void processResponse(String response) {
        if (response != null && !response.isEmpty()) {
            // Create a set to track added server IPs
            Set<String> addedServerIPs = new HashSet<>();
            MinecraftClient client = MinecraftClient.getInstance();
            ServerList serverList = new ServerList(client);

            // Parse the response into lines
            String[] lines = response.split("\n");
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length >= 16) { // Ensure there are enough parts
                    String ip = parts[0].trim();
                    String portString = parts[1].trim();
                    String countryCode = parts[4].trim(); // Extract country code

                    int port;
                    try {
                        port = Integer.parseInt(portString);
                    } catch (NumberFormatException e) {
                        continue; // Skip invalid port numbers
                    }

                    // Check if the IP has already been added
                    if (addedServerIPs.add(ip)) {
                        // Create formatted server name
                        String serverName = ip + ":" + port;
                        if (!countryCode.isEmpty()) {
                            serverName += " (" + countryCode + ")";
                        }

                        // Create ServerInfo object
                        ServerInfo serverInfo = new ServerInfo(serverName, ip + ":" + port, ServerInfo.ServerType.OTHER);

                        // Add the server to the server list
                        serverList.add(serverInfo, false);
                    }
                }
            }

            // Save the updated server list
            serverList.saveFile();

            // Update button text to indicate completion
            updateButtonText("amyseeker | done");
        } else {
            System.out.println("Failed to fetch server data: Response is empty.");
            updateButtonText("amyseeker | error");
        }
    }

    private void updateButtonText(String text) {
        // Update the button text on the main thread
        MinecraftClient.getInstance().execute(() -> amySeekerButton.setMessage(Text.of(text)));
    }
}