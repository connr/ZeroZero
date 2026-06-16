package com.zerostreak;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Hitsplat;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
		name = "Zero Streak",
		description = "Plays a sound when you hit too many zeros in a row",
		tags = {"combat", "sound", "zero", "splash", "hitsplat"}
)
public class ZeroStreakPlugin extends Plugin
{
	@Inject
	private ZeroStreakConfig config;

	private int consecutiveZeros = 0;

	@Provides
	ZeroStreakConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZeroStreakConfig.class);
	}

	@Override
	protected void startUp()
	{
		consecutiveZeros = 0;
		log.info("Zero Streak plugin started");
	}

	@Override
	protected void shutDown()
	{
		consecutiveZeros = 0;
		log.info("Zero Streak plugin stopped");
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();

		if (!hitsplat.isMine())
		{
			return;
		}

		int amount = hitsplat.getAmount();

		if (amount == 0)
		{
			consecutiveZeros++;
			log.debug("Consecutive zeros: {}", consecutiveZeros);

			if (consecutiveZeros >= config.consecutiveZerosRequired())
			{
				consecutiveZeros = 0;
				playSound();
			}
		}
		else
		{
			consecutiveZeros = 0;
		}
	}

	private void playSound()
	{
		log.debug("Playing zero streak sound!");

		try (InputStream resourceStream = ZeroStreakPlugin.class.getResourceAsStream("zero_streak.wav"))
		{
			if (resourceStream == null)
			{
				log.warn("Could not find zero_streak.wav");
				return;
			}

			AudioInputStream audioStream = AudioSystem.getAudioInputStream(
					new BufferedInputStream(resourceStream));

			Clip clip = AudioSystem.getClip();
			clip.open(audioStream);

			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
			{
				FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				float volume = config.volume() / 100f;
				float dB = (float) (Math.log(Math.max(volume, 0.0001)) / Math.log(10.0) * 20.0);
				dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
				gainControl.setValue(dB);
			}

			clip.start();

			clip.addLineListener(e ->
			{
				if (e.getType() == LineEvent.Type.STOP)
				{
					clip.close();
					try
					{
						audioStream.close();
					}
					catch (IOException ex)
					{
						log.warn("Error closing audio stream", ex);
					}
				}
			});
		}
		catch (UnsupportedAudioFileException | LineUnavailableException | IOException e)
		{
			log.warn("Failed to play zero streak sound", e);
		}
	}
}