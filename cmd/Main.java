package cmd;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Scanner;

public class Main 
{
	public static boolean debug=false, wiiMode=false;
	public static int btNum=0; //1 for BT1/Sparking!, 2 for BT2/Sparking! NEO, 3 for Sparking! METEOR
	private static boolean isCharaCostumePak(RandomAccessFile pak) throws IOException
	{
		//check if PAK is that of the Wii version
		pak.seek(0);
		int initVal = pak.readInt();
		if (initVal<0) wiiMode=true;
		
		int numPakContents = LittleEndian.getInt(initVal);
		pak.seek((numPakContents+1)*4);
		int fileSize = LittleEndian.getInt(pak.readInt());
		int actualFileSize = (int)pak.length();
		if (fileSize==actualFileSize)
		{
			if (numPakContents==168) btNum=1;
			else if (numPakContents==250) btNum=2;
			else if (numPakContents==252) btNum=3;
			if (btNum==0) return false;
			else return true;
		}
		return false;
	}
	public static int getUnsignedByte(byte b)
	{
		return b & 0xFF;
	}
	public static void main(String[] args)
	{
		try
		{
			String helpText = "HQ Shader Converter by ViveTheModder. Works for any Tenkaichi game.\n"
			+ "To see the inner workings of the tool, provide -dbg (or -debug) as an argument.\n"
			+ "Otherwise, use -h (or -help) as an argument to display this text again.";
			if (args.length>0)
			{
				if (args[0].equals("-h") || args[0].equals("-help"))
					System.out.println(helpText);
				else if (args[0].equals("-dbg") || args[0].equals("-debug"))
					debug=true;
				else
				{
					System.out.println("Invalid argument! Use -h (or -help) for help.");
					System.exit(0);
				}
			}
			System.out.println(helpText+"\n");
			
			RandomAccessFile[] paks=null;
			String[] pakNames=null;
			Scanner sc = new Scanner(System.in);
			while (paks==null)
			{
				System.out.println("Enter a valid path to a folder containing character costume PAKs:");
				String path = sc.nextLine();
				File tmp = new File(path);
				if (tmp.isDirectory())
				{
					File[] tmpFiles = tmp.listFiles(new FilenameFilter()
					{
						@Override
						public boolean accept(File dir, String name) 
						{
							String nameLower = name.toLowerCase();
							if (nameLower.endsWith("p.pak") || nameLower.endsWith("_dmg.pak")) return true;
							return false;
						}
					});
					if (tmpFiles!=null && tmpFiles.length>0)
					{
						paks = new RandomAccessFile[tmpFiles.length];
						pakNames = new String[tmpFiles.length];
						for (int i=0; i<tmpFiles.length; i++)
						{
							RandomAccessFile tmpPak = new RandomAccessFile(tmpFiles[i],"rw");
							if (isCharaCostumePak(tmpPak)) 
							{
								paks[i] = tmpPak;
								pakNames[i] = tmpFiles[i].getName(); 
							}
							else System.out.println("Skipping "+tmpFiles[i].getName()+"...");
						}
					}
				}
			}
			sc.close();
			
			long start = System.currentTimeMillis();
			for (int i=0; i<paks.length; i++)
			{
				if (paks[i]!=null) Shader.applyShaderDataToPak(paks[i], pakNames[i]);
			}
			long end = System.currentTimeMillis();
			System.out.println("Time elapsed: "+(end-start)/1000.0+" s");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}