package com.deepdimen.matrix.knowledge.support.speech;

import java.nio.charset.StandardCharsets;

/**
 * Validates the bounded PCM WAV contract accepted by the mobile ASR endpoint.
 */
public final class PcmWavValidator {

	private static final int EXPECTED_SAMPLE_RATE = 16_000;

	private static final int EXPECTED_CHANNELS = 1;

	private static final int EXPECTED_BITS_PER_SAMPLE = 16;

	private static final int PCM_FORMAT = 1;

	private static final long MAX_DURATION_MS = 60_000;

	private static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

	private PcmWavValidator() {
	}

	public static Metadata validate(byte[] audio) {
		if (audio == null || audio.length < 44) {
			throw invalid("音频不是有效的 PCM WAV 文件");
		}
		if (audio.length > MAX_UPLOAD_BYTES) {
			throw new SpeechRecognitionException("AUDIO_SIZE_EXCEEDED", "音频文件不能超过 2 MB");
		}
		if (!asciiEquals(audio, 0, "RIFF") || !asciiEquals(audio, 8, "WAVE")) {
			throw invalid("音频缺少 RIFF/WAVE 文件头");
		}

		long riffEnd = unsignedInt(audio, 4) + 8;
		if (riffEnd != audio.length || riffEnd < 44) {
			throw invalid("WAV 文件长度与 RIFF 声明不一致");
		}

		Format format = null;
		long dataBytes = -1;
		long offset = 12;
		while (offset + 8 <= riffEnd) {
			int chunkOffset = Math.toIntExact(offset);
			String chunkId = new String(audio, chunkOffset, 4, StandardCharsets.US_ASCII);
			long chunkSize = unsignedInt(audio, chunkOffset + 4);
			long dataOffset = offset + 8;
			long chunkEnd = dataOffset + chunkSize;
			if (chunkEnd > riffEnd || chunkEnd < dataOffset) {
				throw invalid("WAV 数据块不完整");
			}

			if ("fmt ".equals(chunkId)) {
				if (format != null || chunkSize < 16) {
					throw invalid("WAV fmt 数据块无效");
				}
				int start = Math.toIntExact(dataOffset);
				format = new Format(unsignedShort(audio, start), unsignedShort(audio, start + 2),
						unsignedInt(audio, start + 4), unsignedInt(audio, start + 8), unsignedShort(audio, start + 12),
						unsignedShort(audio, start + 14));
			}
			else if ("data".equals(chunkId)) {
				if (dataBytes >= 0 || chunkSize == 0) {
					throw invalid("WAV data 数据块无效");
				}
				dataBytes = chunkSize;
			}

			offset = chunkEnd + (chunkSize & 1L);
		}

		if (format == null || dataBytes < 0) {
			throw invalid("WAV 缺少 fmt 或 data 数据块");
		}
		if (offset != riffEnd) {
			throw invalid("WAV 数据块边界无效");
		}
		validateFormat(format);
		if (dataBytes % format.blockAlign() != 0) {
			throw invalid("PCM 数据未按采样帧对齐");
		}
		if (dataBytes * 1_000 > MAX_DURATION_MS * format.byteRate()) {
			throw new SpeechRecognitionException("AUDIO_DURATION_EXCEEDED", "录音时长不能超过 60 秒");
		}
		long durationMs = Math.round((double) dataBytes * 1_000 / format.byteRate());
		return new Metadata(Math.toIntExact(format.sampleRate()), format.channels(), format.bitsPerSample(),
				Math.toIntExact(dataBytes), durationMs);
	}

	private static void validateFormat(Format format) {
		long expectedBlockAlign = (long) format.channels() * format.bitsPerSample() / 8;
		long expectedByteRate = format.sampleRate() * expectedBlockAlign;
		if (format.audioFormat() != PCM_FORMAT || format.sampleRate() != EXPECTED_SAMPLE_RATE
				|| format.channels() != EXPECTED_CHANNELS || format.bitsPerSample() != EXPECTED_BITS_PER_SAMPLE
				|| format.blockAlign() != expectedBlockAlign || format.byteRate() != expectedByteRate) {
			throw invalid("仅支持 16 kHz、16 bit、单声道 PCM WAV");
		}
	}

	private static boolean asciiEquals(byte[] bytes, int offset, String expected) {
		if (offset < 0 || offset + expected.length() > bytes.length) {
			return false;
		}
		for (int index = 0; index < expected.length(); index++) {
			if ((byte) expected.charAt(index) != bytes[offset + index]) {
				return false;
			}
		}
		return true;
	}

	private static int unsignedShort(byte[] bytes, int offset) {
		if (offset < 0 || offset + 2 > bytes.length) {
			throw invalid("WAV 文件头不完整");
		}
		return Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8;
	}

	private static long unsignedInt(byte[] bytes, int offset) {
		if (offset < 0 || offset + 4 > bytes.length) {
			throw invalid("WAV 文件头不完整");
		}
		return Integer.toUnsignedLong(Byte.toUnsignedInt(bytes[offset]) | Byte.toUnsignedInt(bytes[offset + 1]) << 8
				| Byte.toUnsignedInt(bytes[offset + 2]) << 16 | Byte.toUnsignedInt(bytes[offset + 3]) << 24);
	}

	private static SpeechRecognitionException invalid(String message) {
		return new SpeechRecognitionException("AUDIO_FORMAT_INVALID", message);
	}

	private record Format(int audioFormat, int channels, long sampleRate, long byteRate, int blockAlign,
			int bitsPerSample) {
	}

	public record Metadata(int sampleRate, int channels, int bitsPerSample, int dataBytes, long durationMs) {
	}

}
