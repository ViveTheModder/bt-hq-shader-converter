package cmd;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class Shader 
{
	private static final long SHADER_HEADER = 0x4080000000000008L;
	private static final int NUM_SHADER_BYTES = 1024;
	private static int offsetOfShader1;
	private static byte[][] getShaderDataFromPak(RandomAccessFile pak) throws IOException
	{
		//regardless of the game, the MDL is always the 3rd subarchive of a PAK
		pak.seek(12);
		int mdlOffset = LittleEndian.getInt(pak.readInt());
		//as for where the number of shaders is stored, that differs from to game
		int[] numShadersLoc = {22,39,81};
		pak.seek(mdlOffset+numShadersLoc[Main.btNum-1]);
		int numShaders = pak.readByte();
		//so does the offset for the DBT...
		int[] dbtOffsetPos = {16,16,48};
		pak.seek(dbtOffsetPos[Main.btNum-1]);
		int dbtOffset = LittleEndian.getInt(pak.readInt());
		pak.seek(dbtOffset);
		//skip initial DBT data until the first shader is found
		for (int pos=dbtOffset; pos<pak.length(); pos+=8)
		{
			long possibleShaderHeader = pak.readLong();
			if (possibleShaderHeader==SHADER_HEADER)
			{
				offsetOfShader1 = pos+16;
				pak.seek(offsetOfShader1);
				break;
			}
		}
		byte[][] shaderData = new byte[numShaders][NUM_SHADER_BYTES];
		//read shader data
		for (int i=0; i<numShaders; i++)
		{
			byte[] currShader = new byte[NUM_SHADER_BYTES];
			pak.read(currShader);
			shaderData[i] = currShader;
			pak.seek(pak.getFilePointer()+128);
		}
		return shaderData;
	}
	private static byte[] getGradientShader(byte[][] shaderData, int shaderId)
	{
		byte[] oldShader = shaderData[shaderId], newShader = shaderData[shaderId], blank = {0,0,0,(byte)0x80};
		int numSteps=0, posBeforeGradient=0;
		for (int pos=4; pos<1024; pos+=4)
		{
			byte[] nextColor = new byte[4], currColor = new byte[4];
			System.arraycopy(oldShader, pos, nextColor, 0, 4);
			System.arraycopy(oldShader, pos-4, currColor, 0, 4);
			numSteps++;
			if (!(Arrays.equals(currColor, blank) || Arrays.equals(nextColor, blank)))
			{
				if (!Arrays.equals(currColor, nextColor) && numSteps>1)
				{
					if (Main.debug) System.out.println("Colors (different from blank/black) at positions "+(pos-4)+" and "+pos+" are different!");
					posBeforeGradient=pos;
					byte[] newCurrColor = new byte[4], newNextColor = new byte[4];
					System.arraycopy(newShader, pos, newNextColor, 0, 4);
					System.arraycopy(newShader, pos-4, newCurrColor, 0, 4);

					int[] rgbDiff = new int[3]; 
					double[] rgbSteps = new double[3];
					for (int i=0; i<3; i++)
					{
						rgbDiff[i] = Main.getUnsignedByte(newCurrColor[i])-Main.getUnsignedByte(newNextColor[i]);
						rgbSteps[i] = 1.00*rgbDiff[i]/numSteps;
						if (Main.debug) System.out.println("RGB Step "+i+": "+rgbDiff[i]+" / "+numSteps+" = "+rgbSteps[i]);
					}
					pos-=4*numSteps;
					
					double[] oldColorDiffs = new double[3];
					for (int stepCnt=0; stepCnt<numSteps-1; stepCnt++)
					{
						double colorDiff=0;
						pos+=4;
						if (Main.debug) System.out.println("Step Count: "+stepCnt+" (at pos. "+pos+")");
						for (int i=0; i<3; i++)
						{
							if (stepCnt==0) 
							{
								colorDiff=Main.getUnsignedByte(oldShader[pos+i-4])-rgbSteps[i];
								if (Main.debug) System.out.println("Color Diff "+i+": "+Main.getUnsignedByte(oldShader[pos+i-4])+" - "+rgbSteps[i]+" = "+colorDiff);
							}
							else 
							{
								colorDiff=oldColorDiffs[i]-rgbSteps[i];
								if (Main.debug) System.out.println("Color Diff "+i+": "+oldColorDiffs[i]+" - "+rgbSteps[i]+" = "+colorDiff);
							}
							oldColorDiffs[i]=colorDiff;
							/* to get a difference in color, a step is subtracted from the preceding color
							(the i/index is for the byte position [0,1,2], and the -4 is to get us to the preceding color)  */
							byte newColor = (byte)Math.round(colorDiff);
							/* for context, dynamic shaders (for Super Saiyans and such) are designed such that
							there are gaps (parts of the palette that are black), and this messes things up with
							the difference in color, leading to negative values (which can be set to zero with ease) */
							if (newColor<0) newColor=0;
							//apply to new shader if and only if the step is positive (helps reduce metallic sheen)
							if (rgbSteps[i]>0) newShader[pos+i] = newColor;
						}
					}
					pos+=4; //given that the for-loop says "stepCnt<numSteps-1", this assures that the last color is not overwritten
					numSteps=0; //a necessary reset
				}
			}
		}
		
		int posOfLastColor=1020;
		for (int pos=1020; pos>=0; pos-=4)
		{
			byte[] currColor = new byte[4];			
			System.arraycopy(newShader, pos, currColor, 0, 4);
			if (!Arrays.equals(currColor, blank))
			{
				posOfLastColor=pos+4;
				break;
			}
		}
		numSteps = (posOfLastColor-posBeforeGradient)/4;
		
		for (int pos=posBeforeGradient; pos<posOfLastColor; pos++)
		{
			byte[] currColor = new byte[4];
			System.arraycopy(oldShader, pos, currColor, 0, 4);
			//stripped down version of the code above
			double[] rgbSteps = new double[3];
			for (int i=0; i<3; i++)
			{
				rgbSteps[i] = 1.00*Main.getUnsignedByte(currColor[i])/numSteps;
				if (Main.debug) System.out.println("RGB Step "+i+": "+Main.getUnsignedByte(currColor[i])+" / "+numSteps+" = "+rgbSteps[i]);
			}
			double[] oldColorDiffs = new double[3];
			for (int stepCnt=0; stepCnt<numSteps-1; stepCnt++)
			{
				double colorDiff=0;
				pos+=4;
				if (Main.debug) System.out.println("Step Count: "+stepCnt+" (at pos. "+pos+")");
				for (int i=0; i<3; i++)
				{
					if (stepCnt==0) 
					{
						colorDiff=Main.getUnsignedByte(oldShader[pos+i-4])-rgbSteps[i];
						if (Main.debug) System.out.println("Color Diff "+i+": "+Main.getUnsignedByte(oldShader[pos+i-4])+" - "+rgbSteps[i]+" = "+colorDiff);
					}
					else 
					{
						colorDiff=oldColorDiffs[i]-rgbSteps[i];
						if (Main.debug) System.out.println("Color Diff "+i+": "+oldColorDiffs[i]+" - "+rgbSteps[i]+" = "+colorDiff);
					}
					oldColorDiffs[i]=colorDiff;
					byte newColor = (byte)Math.round(colorDiff);
					if (newColor<0) newColor=0;
					newShader[pos+i] = newColor;
				}
			}
			pos+=4;
		}
		return newShader;
 	}
	public static void applyShaderDataToPak(RandomAccessFile pak, String pakName) throws IOException
	{
		byte[][] shaderData = getShaderDataFromPak(pak);
		for (int shaderId=0; shaderId<shaderData.length; shaderId++)
		{
			System.out.println("Converting shader "+(shaderId+1)+" to a gradient shader...");
			byte[] gradientShader = getGradientShader(shaderData,shaderId);
			shaderData[shaderId] = gradientShader;
		}
		pak.seek(offsetOfShader1);
		for (int shaderId=0; shaderId<shaderData.length; shaderId++)
		{
			System.out.println("Applying gradient shader "+(shaderId+1)+" to "+pakName+"...");
			pak.write(shaderData[shaderId]);
			pak.seek(pak.getFilePointer()+128);
		}
		pak.close(); 
	}
}