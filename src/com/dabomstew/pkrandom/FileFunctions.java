package com.dabomstew.pkrandom;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.zip.CRC32;

import com.dabomstew.pkrandom.gui.RandomizerGUI;

public class FileFunctions {

	public static File fixFilename(File original, String defaultExtension) {
		return fixFilename(original, defaultExtension, null);
	}

	// Behavior:
	// if file has no extension, add defaultExtension
	// if there are banned extensions & file has a banned extension, replace
	// with defaultExtension
	// else, leave as is
	public static File fixFilename(File original, String defaultExtension,
			List<String> bannedExtensions) {
		String filename = original.getName();
		if (filename.lastIndexOf('.') >= filename.length() - 5
				&& filename.lastIndexOf('.') != filename.length() - 1
				&& filename.length() > 4 && filename.lastIndexOf('.') != -1) {
			// valid extension, read it off
			String ext = filename.substring(filename.lastIndexOf('.') + 1)
					.toLowerCase();
			if (bannedExtensions != null && bannedExtensions.contains(ext)) {
				// replace with default
				filename = filename.substring(0, filename.lastIndexOf('.') + 1)
						+ defaultExtension;
			}
			// else no change
		} else {
			// add extension
			filename += "." + defaultExtension;
		}
		return new File(original.getAbsolutePath().replace(original.getName(),
				"")
				+ filename);
	}

	private static List<String> overrideFiles = Arrays.asList(new String[] {
			"trainerclasses.txt", "trainernames.txt", "nicknames.txt" });

	public static boolean configExists(String filename) {
		if (overrideFiles.contains(filename)) {
			File fh = new File(RandomizerGUI.getRootPath() + filename);
			if (fh.exists() && fh.canRead()) {
				return true;
			}
			fh = new File("./" + filename);
			if (fh.exists() && fh.canRead()) {
				return true;
			}
		}
		return FileFunctions.class
				.getResource("/com/dabomstew/pkrandom/config/" + filename) != null;
	}

	public static InputStream openConfig(String filename)
			throws FileNotFoundException {
		if (overrideFiles.contains(filename)) {
			File fh = new File(RandomizerGUI.getRootPath() + filename);
			if (fh.exists() && fh.canRead()) {
				return new FileInputStream(fh);
			}
			fh = new File("./" + filename);
			if (fh.exists() && fh.canRead()) {
				return new FileInputStream(fh);
			}
		}
		return FileFunctions.class
				.getResourceAsStream("/com/dabomstew/pkrandom/config/"
						+ filename);
	}

	public static int readFullInt(byte[] data, int offset) {
		ByteBuffer buf = ByteBuffer.allocate(4).put(data, offset, 4);
		buf.rewind();
		return buf.getInt();
	}

	public static int read2ByteInt(byte[] data, int index) {
		return (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8);
	}

	public static byte[] getConfigAsBytes(String filename) throws IOException {
		InputStream in = openConfig(filename);
		byte[] bytes = new byte[in.available()];
		in.read(bytes);
		return bytes;
	}

	public static int getFileChecksum(String filename) {
		try {
			return getFileChecksum(openConfig(filename));
		} catch (IOException e) {
			return 0;
		}
	}

	public static int getFileChecksum(InputStream stream) {
		try {
			Scanner sc = new Scanner(stream, "UTF-8");
			CRC32 checksum = new CRC32();
			while (sc.hasNextLine()) {
				String line = sc.nextLine().trim();
				if (!line.isEmpty()) {
					checksum.update(line.getBytes("UTF-8"));
				}
			}
			sc.close();
			return (int) checksum.getValue();
		} catch (IOException e) {
			return 0;
		}
	}

	public static boolean checkOtherCRC(byte[] data, int byteIndex,
			int switchIndex, String filename, int offsetInData) {
		// If the switch at data[byteIndex].switchIndex is on,
		// then check that the CRC at
		// data[offsetInData] ... data[offsetInData+3]
		// matches the CRC of filename.
		// If not, return false.
		// If any other case, return true.
		int switches = data[byteIndex] & 0xFF;
		if (((switches >> switchIndex) & 0x01) == 0x01) {
			// have to check the CRC
			int crc = readFullInt(data, offsetInData);

			if (getFileChecksum(filename) != crc) {
				return false;
			}
		}
		return true;
	}

	public static byte[] getCodeTweakFile(String filename) throws IOException {
		InputStream is = FileFunctions.class
				.getResourceAsStream("/com/dabomstew/pkrandom/patches/"
						+ filename);
		byte[] buf = new byte[is.available()];
		is.read(buf);
		is.close();
		return buf;
	}

	public static byte[] downloadFile(String url) throws IOException {
		BufferedInputStream in = new BufferedInputStream(
				new URL(url).openStream());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int count;
		while ((count = in.read(buf, 0, 1024)) != -1) {
			out.write(buf, 0, count);
		}
		in.close();
		byte[] output = out.toByteArray();
		return output;
	}

	public static void applyPatch(byte[] rom, String patchName)
			throws IOException {
		byte[] patch = getCodeTweakFile(patchName + ".ips");
		// check sig
		int patchlen = patch.length;
		if (patchlen < 8 || patch[0] != 'P' || patch[1] != 'A'
				|| patch[2] != 'T' || patch[3] != 'C' || patch[4] != 'H') {
			System.out.println("not a valid IPS file");
			return;
		}

		// records
		int offset = 5;
		while (offset + 2 < patchlen) {
			int writeOffset = readIPSOffset(patch, offset);
			if (writeOffset == 0x454f46) {
				// eof, done
				System.out.println("patch successful");
				return;
			}
			offset += 3;
			if (offset + 1 >= patchlen) {
				// error
				System.out
						.println("abrupt ending to IPS file, entry cut off before size");
				return;
			}
			int size = readIPSSize(patch, offset);
			offset += 2;
			if (size == 0) {
				// RLE
				if (offset + 1 >= patchlen) {
					// error
					System.out
							.println("abrupt ending to IPS file, entry cut off before RLE size");
					return;
				}
				int rleSize = readIPSSize(patch, offset);
				if (writeOffset + rleSize > rom.length) {
					// error
					System.out
							.println("trying to patch data past the end of the ROM file");
					return;
				}
				offset += 2;
				if (offset >= patchlen) {
					// error
					System.out
							.println("abrupt ending to IPS file, entry cut off before RLE byte");
					return;
				}
				byte rleByte = patch[offset++];
				for (int i = writeOffset; i < writeOffset + rleSize; i++) {
					rom[i] = rleByte;
				}
			} else {
				if (offset + size > patchlen) {
					// error
					System.out
							.println("abrupt ending to IPS file, entry cut off before end of data block");
					return;
				}
				if (writeOffset + size > rom.length) {
					// error
					System.out
							.println("trying to patch data past the end of the ROM file");
					return;
				}
				System.arraycopy(patch, offset, rom, writeOffset, size);
				offset += size;
			}
		}
		System.out.println("improperly terminated IPS file");
	}

	private static int readIPSOffset(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 16) | ((data[offset + 1] & 0xFF) << 8)
				| (data[offset + 2] & 0xFF);
	}

	private static int readIPSSize(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
	}
}