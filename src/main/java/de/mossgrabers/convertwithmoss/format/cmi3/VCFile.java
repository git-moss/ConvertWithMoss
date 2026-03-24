// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.cmi3;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Accessor to a Fairlight CMI 3 voice (VC) file.
 *
 * @author Jürgen Moßgraber
 */
public class VCFile
{
    private static final int        VC_VERSION_A    = 768;
    private static final int        VC_VERSION_B    = 769;
	
    private static final int        VC_NAME_SIZE   = 16;

    private final INotifier         notifier;
	private int						fileSeeker;
	private int						fileSeeker2;

    private String                  name;


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param vcFile The source file
     */
    public VCFile (final INotifier notifier, final File vcFile) throws IOException, ParseException
    {
        this.notifier = notifier;
		this.name = FileUtils.getNameWithoutType(vcFile);
    }


    /**
     * Get the name.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Read and parse a VC file.
     *
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    public List<IMultisampleSource> read (final InputStream inputStream, final File sourceFile) throws IOException, ParseException
    {
        final DataInputStream in = new DataInputStream (inputStream);
        final List<IGroup> groups = new ArrayList<> ();
        final IGroup group = new DefaultGroup ("CMI3");
		int numSubVoices = 0;
		int voiceTune = 0;
		int channels = 0;
		int fileSeeker = 0;
		int fileSeeker2 = 0;
		int voiceFunctionCount = 0;
		int mappingOffset = 0;

		byte[] inBytes = in.readAllBytes();
		byte[] header = Arrays.copyOfRange(inBytes, 0, 2816);
		DefaultAudioMetadata[] audioMetadata = new DefaultAudioMetadata[256];
		InMemorySampleData[] sampleData = new InMemorySampleData[256];
		DefaultAudioMetadata[] audioMetadataR = new DefaultAudioMetadata[256];
		InMemorySampleData[] sampleDataR = new InMemorySampleData[256];
		List<Integer> subvoiceID = new ArrayList<Integer>();
    	List<Integer> zoneOffset = new ArrayList<Integer>();
    	List<Integer> svID = new ArrayList<Integer>();
    	List<Integer> svIDA = new ArrayList<Integer>();
    	List<Integer> svIDB = new ArrayList<Integer>();
    	List<Integer> svBR = new ArrayList<Integer>();
    	List<Integer> svSizeA = new ArrayList<Integer>();
    	List<Integer> svSizeB = new ArrayList<Integer>();
    	List<Integer> svSR = new ArrayList<Integer>();
    	List<String> svName = new ArrayList<String>();
    	List<Integer> svTune = new ArrayList<Integer>();
    	List<Integer> svWordA = new ArrayList<Integer>();
    	List<Integer> svWordB = new ArrayList<Integer>();
    	List<Integer> svStartA = new ArrayList<Integer>();
    	List<Integer> svStartB = new ArrayList<Integer>();
    	List<Integer> svEndA = new ArrayList<Integer>();
    	List<Integer> svEndB = new ArrayList<Integer>();
    	List<Integer> svLSA = new ArrayList<Integer>();
    	List<Integer> svLSB = new ArrayList<Integer>();
    	List<Integer> svLEA = new ArrayList<Integer>();
    	List<Integer> svLEB = new ArrayList<Integer>();
    	List<Boolean> svIL = new ArrayList<Boolean>();
    	List<Boolean> svLoop = new ArrayList<Boolean>();
    	List<Boolean> svReleaseLoop = new ArrayList<Boolean>();
    	List<Double> svAttackF = new ArrayList<Double>();
    	List<Double> svAttackS = new ArrayList<Double>();
    	List<Double> svHold = new ArrayList<Double>();
    	List<Double> svDecay = new ArrayList<Double>();
    	List<Double> svSustain = new ArrayList<Double>();
    	List<Double> svAmp = new ArrayList<Double>();
    	List<Double> svReleaseF = new ArrayList<Double>();
    	List<Double> svReleaseS = new ArrayList<Double>();
    	List<Boolean> svAttackX = new ArrayList<Boolean>();
    	List<Boolean> svReleaseX = new ArrayList<Boolean>();
		
		
        for (int iPre = 0; iPre < 1; iPre++)
        {
			int checkDone = 0;
			if ((header[0] == 3 && header[1] == 0) || (header[0] == 3 && header[1] == 1))
				checkDone++;
			else
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER"));
			channels = Byte.toUnsignedInt(header[16]) >= 127 ? 2 : 1;
			voiceFunctionCount = (int)header[19];
			for (int i = 0; i < 128; i++)
			{
				if (header[i * 4 + 259] == 0 || header[i * 4 + 259] < 0)
				{
					numSubVoices = i;
					break;
				}
				else
				{
					subvoiceID.add(Byte.toUnsignedInt(header[i * 4 + 259]));
				}
			}

			for (int i = 0; i < numSubVoices; i++)
			{
				zoneOffset.add((int) Byte.toUnsignedInt(header[i*4+256]) * 16777216 + Byte.toUnsignedInt(header[i*4+257]) * 65536 + Byte.toUnsignedInt(header[i*4+258]) * 256);
				svID.add((int)header[i*4+259]);
				svIDA.add(0);
				svIDB.add(0);
				svBR.add(16);
				svSizeA.add(0);
				svSizeB.add(0);
				svSR.add(44100);
				svName.add("");
				svTune.add(0);
				svWordA.add(0);
				svWordB.add(0);
				svStartA.add(0);
				svStartB.add(0);
				svEndA.add(0);
				svEndB.add(0);
				svLSA.add(0);
				svLSB.add(0);
				svLEA.add(0);
				svLEB.add(0);
				svLoop.add(false);
				svReleaseLoop.add(false);
				svAttackF.add(0.0);
				svAttackS.add(0.0);
				svHold.add(0.0);
				svDecay.add(0.0);
				svSustain.add(0.0);
				svAmp.add(0.0);
				svReleaseF.add(0.0);
				svReleaseS.add(0.0);
				svAttackX.add(false);
				svReleaseX.add(false);
				svIL.add(false);
			}
			
			byte[] headFuncBuff = Arrays.copyOfRange(header, 768, 768 + 2);
			int skipped = 0;
			while (Byte.toUnsignedInt(headFuncBuff[1]) > 2 && headFuncBuff[1] != 11)
			{
				int entrySize = Byte.toUnsignedInt(headFuncBuff[1]);
				skipped += 2;
				headFuncBuff = Arrays.copyOfRange(header, 768 + skipped, 768 + skipped + entrySize);
				switch(headFuncBuff[0])
				{
					case 6:
					{
						mappingOffset = 770 + skipped;
						break;
					}
					case 9:
					{
						switch(headFuncBuff[2])
						{
							case 24:
							{
								voiceTune = (int) Byte.toUnsignedInt(headFuncBuff[4]) * 256 + Byte.toUnsignedInt(headFuncBuff[5]);
							}
							default:
							{
								break;
							}
						}
						break;
					}
					default:
					{
						break;
					}
				}
				skipped += entrySize;
				headFuncBuff = Arrays.copyOfRange(header, 768 + skipped, 768 + skipped + 2);
			}
			for (int itera = 0; itera < numSubVoices; itera++)
			{
				fileSeeker = zoneOffset.get(itera);
				byte[] sub = Arrays.copyOfRange(inBytes, fileSeeker, fileSeeker + 768);
				svIDA.set(itera, (int)sub[16]);
				svBR.set(itera, (int)sub[17] == 2 ? 16 : 8);
				svSizeA.set(itera, (int) Byte.toUnsignedInt(sub[18]) * 16777216 + Byte.toUnsignedInt(sub[19]) * 65536 + Byte.toUnsignedInt(sub[20]) * 256 + Byte.toUnsignedInt(sub[21]));
				int srTemp = (int) Byte.toUnsignedInt(sub[22]) * 16777216 + Byte.toUnsignedInt(sub[23]) * 65536 + Byte.toUnsignedInt(sub[24]) * 256 + Byte.toUnsignedInt(sub[25]);
				svSR.set(itera, srTemp == 0 ? 44100 : srTemp);
				if (channels == 2)
				{
					svIDB.set(itera, (int)sub[33]);
					svSizeB.set(itera, (int) Byte.toUnsignedInt(sub[34]) * 16777216 + Byte.toUnsignedInt(sub[35]) * 65536 + Byte.toUnsignedInt(sub[36]) * 256 + Byte.toUnsignedInt(sub[37]));
				}
				byte[] nameBuff = Arrays.copyOfRange(sub,42,58);
				if (nameBuff[0] == 0x00)
				{
					String tempName = this.name.concat("_").concat(String.valueOf(itera + 1));
					svName.set(itera, tempName);
				}
				else
				{
					for (int stringIt = 0; stringIt < 16; stringIt++)
					{
						if (nameBuff[stringIt] == 0x00)
							break;
						byte[] tempBytes = new byte[1];
						tempBytes[0] = (byte)(nameBuff[stringIt] & 0x7F);
						svName.set(itera, svName.get(itera).concat(new String(tempBytes, "UTF-8")));
					}
				}
				svName.set(itera, svName.get(itera).concat("_").concat(String.format("%03d", itera)));
				int subStart = fileSeeker + 256;
				byte[] subFuncBuff = Arrays.copyOfRange(inBytes, subStart, subStart + 2);
				while (Byte.toUnsignedInt(subFuncBuff[1]) > 2 && subFuncBuff[1] != 11)
				{
					int subEntrySize = Byte.toUnsignedInt(subFuncBuff[1]);
					subStart += 2;
					subFuncBuff = Arrays.copyOfRange(inBytes, subStart, subStart + subEntrySize);
					switch(subFuncBuff[0])
					{
						case 9:
						{
							switch(subFuncBuff[2])
							{
								case 5:
								{
									svAttackF.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svAttackF.get(itera) > 32767)
										svAttackF.set(itera, 65536 - svAttackF.get(itera));
									svAttackF.set(itera, svAttackF.get(itera) / 4096);
									break;
								}
								case 6:
								{
									svHold.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svHold.get(itera) > 32767)
										svHold.set(itera, 65536 - svHold.get(itera));
									svHold.set(itera, svHold.get(itera) / 4096);
									break;
								}
								case 7:
								{
									svDecay.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svDecay.get(itera) > 32767)
										svDecay.set(itera, 65536 - svDecay.get(itera));
									svDecay.set(itera, svDecay.get(itera) / 2048);
									break;
								}
								case 8:
								{
									svSustain.set(itera, (double) levelConvert(Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5])));
									break;
								}
								case 9:
								{
									svAmp.set(itera, (double) levelConvertDB(Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5])));
									break;
								}
								case 10:
								{
									svReleaseF.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svReleaseF.get(itera) > 32767)
										svReleaseF.set(itera, 65536 - svReleaseF.get(itera));
									svReleaseF.set(itera, svReleaseF.get(itera) / 2048);
									break;
								}
								case 16:
								{
									svAttackS.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svAttackS.get(itera) > 32767)
										svAttackS.set(itera, 65536 - svAttackS.get(itera));
									svAttackS.set(itera, svAttackS.get(itera) / 4096);
									break;
								}
								case 17:
								{
									svReleaseS.set(itera, (double) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									if (svReleaseS.get(itera) > 32767)
										svReleaseS.set(itera, 65536 - svReleaseS.get(itera));
									svReleaseS.set(itera, svReleaseS.get(itera) / 2048);
									break;
								}
								case 24:
								{
									svTune.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[4]) * 256 + Byte.toUnsignedInt(subFuncBuff[5]));
									break;
								}
								case 27:
								{
									svAttackX.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[3]) > 127 ? true : false);
									break;
								}
								case 28:
								{
									svReleaseX.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[3]) > 127 ? true : false);
									break;
								}
								case 29:
								{
									svLoop.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[3]) > 127 ? true : false);
									break;
								}
								case 42:
								{
									svReleaseLoop.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[3]) > 127 ? true : false);
									break;
								}
								default:
								{
									break;
								}
							}
							break;
						}
						case 13:
						{
							svWordA.set(itera, (int)subFuncBuff[3]);
							svStartA.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[4]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[5]) * 65536 + Byte.toUnsignedInt(subFuncBuff[6]) * 256 + Byte.toUnsignedInt(subFuncBuff[7]));
							svEndA.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[8]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[9]) * 65536 + Byte.toUnsignedInt(subFuncBuff[10]) * 256 + Byte.toUnsignedInt(subFuncBuff[11]));
							svLSA.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[12]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[13]) * 65536 + Byte.toUnsignedInt(subFuncBuff[14]) * 256 + Byte.toUnsignedInt(subFuncBuff[15]));
							svLEA.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[16]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[17]) * 65536 + Byte.toUnsignedInt(subFuncBuff[18]) * 256 + Byte.toUnsignedInt(subFuncBuff[19]));
							break;
						}
						case 18:
						{
							svWordB.set(itera, (int)subFuncBuff[3]);
							svStartB.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[4]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[5]) * 65536 + Byte.toUnsignedInt(subFuncBuff[6]) * 256 + Byte.toUnsignedInt(subFuncBuff[7]));
							svEndB.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[8]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[9]) * 65536 + Byte.toUnsignedInt(subFuncBuff[10]) * 256 + Byte.toUnsignedInt(subFuncBuff[11]));
							svLSB.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[12]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[13]) * 65536 + Byte.toUnsignedInt(subFuncBuff[14]) * 256 + Byte.toUnsignedInt(subFuncBuff[15]));
							svLEB.set(itera, (int) Byte.toUnsignedInt(subFuncBuff[16]) * 16777216 + Byte.toUnsignedInt(subFuncBuff[17]) * 65536 + Byte.toUnsignedInt(subFuncBuff[18]) * 256 + Byte.toUnsignedInt(subFuncBuff[19]));
							break;
						}
						default:
						{
							break;
						}
					}
					subStart += subEntrySize;
					subFuncBuff = Arrays.copyOfRange(inBytes, subStart, subStart + 2);
				}
				
				if (channels == 2 && svWordA.get(itera) == svWordB.get(itera))
				{
					if (svStartA.get(itera) - svStartB.get(itera) == 0)
					{
						if (svEndA.get(itera) - svEndB.get(itera) == 0)
						{
							if (svLSA.get(itera) - svLSB.get(itera) == 0)
							{
								if (svLEA.get(itera) - svLEB.get(itera) == 0)
								{
									svIL.set(itera, true);
								}
							}
						}
					}
				}
						
        		byte [] data = null;
        		byte [] data2 = null;
				fileSeeker = zoneOffset.get((svIDA).indexOf(svIDA.get(itera))) + 2304;
				byte[] sampleBuff1 = Arrays.copyOfRange(inBytes, fileSeeker, fileSeeker + svSizeA.get(itera));
				if (channels == 2 && svIL.get(itera) == true)
				{
					fileSeeker2 = fileSeeker + svSizeA.get(itera);
					if ((svIDB.get(itera) & 127) != svIDA.get(itera))
					{
						fileSeeker2 = zoneOffset.get(svIDB.get((svIDB).indexOf(svIDB.get(itera)))) + 2304;
						if (svIDB.get((svIDB).indexOf(svIDB.get(itera))) < 0)
							fileSeeker2 += svSizeA.get(itera);
					}
					byte[] sampleBuff2 = Arrays.copyOfRange(inBytes, fileSeeker2, fileSeeker2 + svSizeA.get(itera));
					
					data = new byte[svSizeA.get(itera) * 2];
					
					for (int dataCount = 0; dataCount < svSizeA.get(itera) / 2; dataCount++)
					{
						data[dataCount * 4 + 0] = sampleBuff1[dataCount * 2 + 0];
						data[dataCount * 4 + 1] = sampleBuff1[dataCount * 2 + 1];
						data[dataCount * 4 + 2] = sampleBuff2[dataCount * 2 + 0];
						data[dataCount * 4 + 3] = sampleBuff2[dataCount * 2 + 1];
					}
				}
				else
				{
					data = sampleBuff1;
				}
				

                if (svBR.get(itera) == 16)
                    flipBytes(data);
				else
					flipBits(data);
				
        		audioMetadata[itera] = ( (new DefaultAudioMetadata ((svIL.get(itera) == true ? 2 : 1), svSR.get(itera), svBR.get(itera), svSizeA.get(itera) / 2)));
        		sampleData[itera] = ( (new InMemorySampleData (audioMetadata[itera], data)));
				
				if (channels == 2 && svIL.get(itera) == false)
				{
					
					if (svIDB.get(itera) % 128 != svIDA.get(itera))
					{
						fileSeeker2 = zoneOffset.get((svIDB).indexOf(svIDB.get(itera))) + 2304;
						if (svIDB.get((svIDB).indexOf(svIDB.get(itera))) > 127 || svIDB.get((svIDB).indexOf(svIDB.get(itera))) < 0)
						{
							fileSeeker2 += svSizeA.get(itera);
						}
					}
					else
					{
						fileSeeker2 = zoneOffset.get((svIDB).indexOf(svIDB.get(itera))) + 2304;
					}
					byte[] sampleBuff2 = Arrays.copyOfRange(inBytes, fileSeeker2, fileSeeker2 + svSizeA.get(itera));
					data2 = sampleBuff2;
                    flipBytes(data2);
	        		audioMetadataR[itera] = ( (new DefaultAudioMetadata ((svIL.get(itera) == true ? 2 : 1), svSR.get(itera), svBR.get(itera), svSizeB.get(itera) / 2)));
	        		sampleDataR[itera] = ( (new InMemorySampleData (audioMetadata[itera], data2)));
				}
			}
			byte[] mappingInfo = Arrays.copyOfRange(inBytes, mappingOffset, mappingOffset + 128);
			int prevK = -1;
			int curr = 0;
			for (int key = 0; key < 128; key++)
			{
						if (Byte.toUnsignedInt(mappingInfo[key]) <= curr || mappingInfo[key] > numSubVoices)
						{
							continue;
						}
						if (Byte.toUnsignedInt(mappingInfo[key]) == prevK)
						{
							continue;
						}
						if (subvoiceID.indexOf(Byte.toUnsignedInt(mappingInfo[key])) == -1)
						{
							continue;
						}
						
						DefaultSampleZone newZone = new DefaultSampleZone();
						newZone.setKeyLow(key);
						for (int key2 = key; key2 < 128; key2++)
						{
							if (Byte.toUnsignedInt(mappingInfo[key]) != Byte.toUnsignedInt(mappingInfo[key2]))
							{
								newZone.setKeyHigh(key2 - 1);
								break;
							}
						}
						int firstID = subvoiceID.indexOf(Byte.toUnsignedInt(mappingInfo[key]));
						newZone.setName(svName.get(firstID));
       		 			newZone.setSampleData(sampleData[firstID]);
						if (svTune.get(firstID) == -1)
						{
							newZone.setKeyTracking(0);
							newZone.setKeyRoot(65);
						}
						else
						{
							newZone.setKeyTracking(1);
							newZone.setKeyRoot((int)Math.round(pitchConvert(svTune.get(firstID), voiceTune, svSR.get(firstID))));
							newZone.setTuning((pitchConvert(svTune.get(firstID), voiceTune, svSR.get(firstID)) - newZone.getKeyRoot()) / -1.0);
							newZone.setKeyRoot(newZone.getKeyRoot() < 0 ? newZone.getKeyRoot() + 128 : newZone.getKeyRoot());
						}
						if (svLoop.get(firstID) == true)
						{
                    		DefaultSampleLoop loop = new DefaultSampleLoop ();
                    		loop.setStart (svLSA.get(firstID));
                    		loop.setEnd (svLEA.get(firstID));
							newZone.addLoop(loop);
						}
						
						newZone.setGain(svAmp.get(firstID));
        				final IEnvelope amplitudeEnvelope = newZone.getAmplitudeEnvelopeModulator ().getSource ();
						if (svAttackX.get(firstID) == true)
							amplitudeEnvelope.setAttackTime(svAttackS.get(firstID));
						else
							amplitudeEnvelope.setAttackTime(svAttackF.get(firstID));
						amplitudeEnvelope.setHoldTime(svHold.get(firstID));
						amplitudeEnvelope.setDecayTime(svDecay.get(firstID));
						amplitudeEnvelope.setSustainLevel(svSustain.get(firstID));
						if (svReleaseX.get(firstID) == true)
							amplitudeEnvelope.setReleaseTime(svReleaseS.get(firstID));
						else
							amplitudeEnvelope.setReleaseTime(svReleaseF.get(firstID));
						
						if (channels == 2 && svIL.get(firstID) == false)
						{
							newZone.setPanning(-1);
							newZone.setName(newZone.getName().concat("_L"));
							DefaultSampleZone newZone2 = new DefaultSampleZone();
							newZone2.setPanning(1);
							newZone2.setKeyLow(key);
							for (int key2 = key; key2 < 128; key2++)
							{
								if (Byte.toUnsignedInt(mappingInfo[key]) != Byte.toUnsignedInt(mappingInfo[key2]))
								{
									newZone2.setKeyHigh(key2 - 1);
									break;
								}
							}
							int secondID = subvoiceID.indexOf(Byte.toUnsignedInt(mappingInfo[key]));
							newZone2.setName(svName.get(secondID).concat("_R"));
       		 				newZone2.setSampleData(sampleDataR[secondID]);
							if (svTune.get(secondID) == -1)
							{
								newZone2.setKeyTracking(0);
								newZone2.setKeyRoot(60);
							}
							else
							{
								newZone2.setKeyTracking(1);
								newZone2.setKeyRoot((int)Math.round(pitchConvert(svTune.get(secondID), voiceTune, svSR.get(secondID))));
								newZone2.setTuning((pitchConvert(svTune.get(secondID), voiceTune, svSR.get(secondID)) - newZone2.getKeyRoot()) / -1.0);
								newZone2.setKeyRoot(newZone2.getKeyRoot() < 0 ? newZone2.getKeyRoot() + 128 : newZone2.getKeyRoot());
							}
							if (svLoop.get(secondID) == true)
							{
                    			DefaultSampleLoop loop = new DefaultSampleLoop();
                    			loop.setStart (svLSB.get(secondID));
                    			loop.setEnd (svLEB.get(secondID));
								newZone2.addLoop(loop);
							}
						
							newZone2.setGain(svAmp.get(secondID));
        					final IEnvelope amplitudeEnvelope2 = newZone2.getAmplitudeEnvelopeModulator ().getSource ();
							if (svAttackX.get(secondID) == true)
								amplitudeEnvelope2.setAttackTime(svAttackS.get(secondID));
							else
								amplitudeEnvelope2.setAttackTime(svAttackF.get(secondID));
							amplitudeEnvelope2.setHoldTime(svHold.get(secondID));
							amplitudeEnvelope2.setDecayTime(svDecay.get(secondID));
							amplitudeEnvelope2.setSustainLevel(svSustain.get(secondID));
							if (svReleaseX.get(secondID) == true)
								amplitudeEnvelope2.setReleaseTime(svReleaseS.get(secondID));
							else
								amplitudeEnvelope2.setReleaseTime(svReleaseF.get(secondID));
						
            				group.addSampleZone(newZone2);
						}
            			group.addSampleZone(newZone);
						prevK = Byte.toUnsignedInt(mappingInfo[key]);
			}
        }
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFile.getParentFile (), this.name);
		final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, this.name, AudioFileUtils.subtractPaths (sourceFile.getParentFile (), sourceFile));
        final IMetadata metadata = multisampleSource.getMetadata();
        try
        {
            final BasicFileAttributes attrs = Files.readAttributes (sourceFile.toPath (), BasicFileAttributes.class);
            final FileTime creationTime = attrs.creationTime ();
            final FileTime modifiedTime = attrs.lastModifiedTime ();
            final long creationTimeMillis = creationTime.toMillis ();
            final long modifiedTimeMillis = modifiedTime.toMillis ();
            metadata.setCreationDateTime (new Date (creationTimeMillis < modifiedTimeMillis ? creationTimeMillis : modifiedTimeMillis));
        }
        catch (final IOException ex)
        {
            metadata.setCreationDateTime (new Date ());
        }
        if (!group.getSampleZones ().isEmpty ())
		{
        	groups.add(group);
		}
		multisampleSource.setGroups(groups);
		return Collections.singletonList(multisampleSource);
    }
	
	private double pitchConvert(final int inV, final int gV, final int srV)
	{
		int outV = inV;
		if (outV >= 16384)
			outV -= 32768;
		int outGV = gV;
		if (outGV >= 16384)
			outGV -= 32768;
		double sr0 = Math.log((double)(srV) / 44701.0) / Math.log(2);
		return ((-outV - outGV) / 256.0 + (sr0 * 12) + 65) % 128;
	}
	
	private double levelConvert(final int inV)
	{
	    if (inV == 0)
	        return 1;
	    double outV = (double) inV;
	    if (outV >= 32768)
	        outV -= 65536;
	    return Math.max(0, 1.01 - Math.pow(10, outV / 256) / 100);
	}
    
	private double levelConvertDB(final int inV)
	{
    	double outV = inV;
    	if (outV >= 32768)
       	 	outV -= 65536;
    	return outV / 512;
	}

    // Flip MSB / LSB
    private static void flipBytes (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
        {
            byte temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
    }

    // Flip MSB / LSB
    private static void flipBits (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
        {
            data[i] = (byte)(data[i] ^ 128);
        }
    }
}
