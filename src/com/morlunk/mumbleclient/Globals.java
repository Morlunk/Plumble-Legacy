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
	public static final String PROTOCOL_VERSION_STRING = "1.2.4";
	public static final int CELT_VERSION = 0x8000000b;
	
	public static final int SHOWCASE_SERVER_ADD = 0;
	public static final int SHOWCASE_PUSH_TO_TALK = 1;
	public static final int SHOWCASE_SWITCHER = 2;
	
}
