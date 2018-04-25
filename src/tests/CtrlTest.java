package tests;

import java.lang.Runtime;
import java.lang.Process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
public class CtrlTest {
	public static void main(String[] args) throws IOException {
		System.out.println(CtrlTest.class.getProtectionDomain().getCodeSource().toString());
		
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		Runtime runtime = Runtime.getRuntime();
		try {
			Process checkProc = runtime.exec("PowerShell.exe -Command \""
					+ "if((Get-Process | Where ProcessName -eq taskmgr) -eq $null) { echo n } else { echo y }"
					+ "\"");
			char output = (char) checkProc.getInputStream().read();
			if(output == 'y') {
				runtime.exec("PowerShell.exe -Command \"Stop-Process -ProcessName taskmgr\"");
			}
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		while(true) {
			System.out.println("Command:");
			String cmd = input.readLine();
			//Process cmdProcess = runtime.exec("cmd /c " + cmd);
			Process cmdProcess = runtime.exec(cmd);
			BufferedReader cmdOut = new BufferedReader(new InputStreamReader(cmdProcess.getInputStream()));
			BufferedReader cmdErr = new BufferedReader(new InputStreamReader(cmdProcess.getErrorStream()));
			System.out.println("Output:");
			String s;
			while((s = cmdOut.readLine()) != null) {
				System.out.println(s);
			}
			System.out.println("<END>\nError stream:");
			while((s = cmdErr.readLine()) != null) {
				System.out.println(s);
			}
			System.out.println("<END>\n");
		}
	}
}
