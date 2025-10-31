package nl.doonline.ZSCompetitions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("zsc_eventtracker")
public interface EventTrackerConfig extends Config
{
	@ConfigSection(
		name = "Connection Settings",
		description = "Settings for connecting to the external webhook server.",
		position = 0
	)
	String connectionSettings = "connectionSettings";

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Webhook URL",
		description = "The URL to send event data to.",
		section = connectionSettings
	)
	default String webhookUrl()
	{
		return "http://localhost:8080/webhook";
	}

	@ConfigItem(
		keyName = "enableConnectionHandling",
		name = "Enable Connection Handling",
		description = "If enabled, the plugin will periodically check connection to the webhook URL and notify on prolonged disconnection.",
		section = connectionSettings
	)
	default boolean enableConnectionHandling()
	{
		return true;
	}

	@ConfigItem(
		keyName = "retryDelaySeconds",
		name = "Retry Delay (seconds)",
		description = "How long to wait before retrying connection after a failure.",
		section = connectionSettings
	)
	default int retryDelaySeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "popupDelayMinutes",
		name = "Popup Delay (minutes)",		description = "How long to wait before showing a popup notification for prolonged disconnection.",
		section = connectionSettings
	)
	default int popupDelayMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "pollPort",
		name = "Polling Server Port",
		description = "The port for the local polling server.",
		section = connectionSettings
	)
	default int pollPort()
	{
		return 1464;
	}

	@ConfigItem(
		keyName = "postEndpoint",
		name = "Webhook Post Endpoint",
		description = "The endpoint for posting webhook events.",
		section = connectionSettings
	)
	default String postEndpoint()
	{
		return "http://localhost:1664/webhook";
	}
	@ConfigSection(
		name = "Event Settings",
		description = "Configure which events to track and push.",
		position = 1
	)
	String eventSettings = "eventSettings";

	@ConfigItem(
		keyName = "pushActorPositionUpdates",
		name = "Push Actor Position Updates",
		description = "If enabled, actor position updates will be pushed on every game tick.",
		section = eventSettings
	)
	default boolean pushActorPositionUpdates()
	{
		return false;
	}

	@ConfigSection(
		name = "Render Settings",
		description = "Configure rendering-related settings.",
		position = 2
	)
	String renderSettings = "renderSettings";

	@Range(
		min = 1,
		max = 6
	)
	@ConfigItem(
		keyName = "tileRenderRadius",
		name = "Tile Render Radius",		description = "The radius around the player for which tiles are rendered. (Requires client restart)",
		section = renderSettings
	)
	default int tileRenderRadius()
	{
		return 5;
	}

}
