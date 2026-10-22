package com.deepdimen.matrix.knowledge.support.speech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

class PcmWavValidatorTest {

	@Test
	void acceptsSixteenKhzSixteenBitMonoPcmWav() {
		byte[] wav = pcmWav(16_000, 1, 16, 32_000);

		PcmWavValidator.Metadata metadata = PcmWavValidator.validate(wav);

		assertEquals(16_000, metadata.sampleRate());
		assertEquals(1, metadata.channels());
		assertEquals(16, metadata.bitsPerSample());
		assertEquals(32_000, metadata.dataBytes());
		assertEquals(1_000, metadata.durationMs());
	}

	@Test
	void rejectsNonPcmOrWrongCaptureFormat() {
		assertFormatError(pcmWav(8_000, 1, 16, 16_000));
		assertFormatError(pcmWav(16_000, 2, 16, 64_000));
		assertFormatError(pcmWav(16_000, 1, 8, 16_000));
	}

	@Test
	void rejectsAudioLongerThanSixtySeconds() {
		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> PcmWavValidator.validate(pcmWav(16_000, 1, 16, 32_000 * 60 + 2)));

		assertEquals("AUDIO_DURATION_EXCEEDED", error.getCode());
	}

	@Test
	void acceptsExactlySixtySeconds() {
		assertEquals(60_000, PcmWavValidator.validate(pcmWav(16_000, 1, 16, 32_000 * 60)).durationMs());
	}

	@Test
	void rejectsTruncatedRiffChunks() {
		byte[] wav = pcmWav(16_000, 1, 16, 32_000);
		ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, wav.length);

		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> PcmWavValidator.validate(wav));

		assertEquals("AUDIO_FORMAT_INVALID", error.getCode());
	}

	@Test
	void rejectsTrailingBytesOutsideTheDeclaredRiff() {
		byte[] wav = pcmWav(16_000, 1, 16, 320);
		byte[] withTrailingBytes = java.util.Arrays.copyOf(wav, wav.length + 4);

		assertFormatError(withTrailingBytes);
	}

	@Test
	void rejectsPcmDataThatIsNotFrameAligned() {
		assertFormatError(pcmWav(16_000, 1, 16, 319));
	}

	private static void assertFormatError(byte[] wav) {
		SpeechRecognitionException error = assertThrows(SpeechRecognitionException.class,
				() -> PcmWavValidator.validate(wav));
		assertEquals("AUDIO_FORMAT_INVALID", error.getCode());
	}

	private static byte[] pcmWav(int sampleRate, int channels, int bitsPerSample, int dataBytes) {
		int blockAlign = channels * bitsPerSample / 8;
		int byteRate = sampleRate * blockAlign;
		ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		putAscii(buffer, "RIFF");
		buffer.putInt(36 + dataBytes);
		putAscii(buffer, "WAVE");
		putAscii(buffer, "fmt ");
		buffer.putInt(16);
		buffer.putShort((short) 1);
		buffer.putShort((short) channels);
		buffer.putInt(sampleRate);
		buffer.putInt(byteRate);
		buffer.putShort((short) blockAlign);
		buffer.putShort((short) bitsPerSample);
		putAscii(buffer, "data");
		buffer.putInt(dataBytes);
		return buffer.array();
	}

	private static void putAscii(ByteBuffer buffer, String value) {
		for (int index = 0; index < value.length(); index++) {
			buffer.put((byte) value.charAt(index));
		}
	}

}
