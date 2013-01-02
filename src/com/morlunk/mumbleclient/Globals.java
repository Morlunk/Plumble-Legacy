package com.morlunk.mumbleclient;

/**
 * Constant global values
 *
 * @author Rantanen
 */
public class Globals {
	public static final String LOG_TAG = "Plumble";
	public static final int PROTOCOL_VERSION = (1 << 16) | (2 << 8) |
											   (4 & 0xFF);
	public static final int CELT_VERSION = 0x8000000b;
}
