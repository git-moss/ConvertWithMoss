package de.mossgrabers.convertwithmoss.format.nki;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.InflaterInputStream;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;

public class NkiDetectorTask extends AbstractDetectorTask {

    private static final int K2_OFFSET   = 0xAA;
    private static final int NISS_OFFSET = 0x24;
    
    enum NIFormat {K2, NISS};
    
    /** stores the currently processed file */
	private File file;
    
    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */	
	public NkiDetectorTask(final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata) {
        super (notifier, consumer, sourceFolder, metadata, ".nki");
	}

	
	
	@Override
	protected List<IMultisampleSource> readFile(File sourceFile) {
        if (this.waitForDelivery ())
            return Collections.emptyList ();
        
        long offset = determineCompressedDataOffset(sourceFile);

        try
        {
            final String content = this.loadCompressedTextFile (sourceFile, offset);
            
            AbstractNKIMetadataFileParser metadataFileParser = null;
            
            if(offset == K2_OFFSET) 
            	metadataFileParser = new K2MetadataFileParser(notifier, metadata, sourceFolder, file);
            else if(offset == NISS_OFFSET)
            	metadataFileParser = new NiSSMetaDataFileParser(notifier, metadata, sourceFolder, file);
            else
            	return Collections.emptyList();
            
            if (this.waitForDelivery ())
                return Collections.emptyList ();
            
        	return metadataFileParser.parseMetadataFile(sourceFile, content);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
	}
	
	/**
	 * Determines the offset of the compressed xml in the source file.
	 * 
	 * @param sourceFile the source file
	 * @return the offset, if zlib signature was found, -1 else
	 */
	private long determineCompressedDataOffset(File sourceFile) {
		long knownOffsets[] = {NISS_OFFSET, K2_OFFSET};
    	    	
    	byte buffer[] = new byte[2];
        for(long offset : knownOffsets) {
        	FileInputStream in = null;        	
        	
        	try {
    			in = new FileInputStream(sourceFile);
    		} catch (FileNotFoundException e2) {
    			return -1;
    		}
        	
        	try {
				in.skip(offset);
	            int numBytesRead = in.read(buffer);

	            if(numBytesRead != 2) {
	            	in.close();
	            	continue;
	            }
	                
	            
	            if(isZlibSignature(buffer)) {
	            	in.close();
	            	return offset;
	            }				
			} catch (IOException e) {
				// intentionally left empty
			}
        	
            try {
    			in.close();
    		} catch (IOException e) {
    			// intentionally left empty
    		}
        }
        	
        return -1;
	}


    /**
     * Test whether a byte array starts with a zlib signature.
     * 
     * @param byteArr the byte array
     * @return true if byte array starts with a zlib signature, false else
     */
	private boolean isZlibSignature(byte[] byteArr) {
		final byte firstSignatureByte = 0x78;
		final byte secondSignatureBytes[] = {0x01, 0x5E, (byte) 0x9C, (byte) 0xDA, 0x20, 0x7D, (byte) 0xBB, (byte) 0xF9};
		
		if(byteArr == null)
			return false;
		
		if(byteArr.length < 2)
			return false;
		
		if(firstSignatureByte != byteArr[0])
			return false;
		
		for(byte expected : secondSignatureBytes) {
			if(expected == byteArr[1])
				return true;
		}
		
		return false;
	}



	/**
     * Loads a zip-compressed file in UTF-8 encoding. If UTF-8 fails a string is created anyway but with
     * unspecified behavior.s
     *
     * @param file The file to load
     * @param offset the offset where the zip-compressed part begins
     * @return The loaded text
     * @throws IOException Could not load the file
	 */
    private String loadCompressedTextFile(File file, long offset) throws IOException {
    	this.file = file;
		InputStream inputStream = null;  
		String result = "";
        FileInputStream fileInputStream = new FileInputStream(file);
        fileInputStream.skip(offset);
		inputStream = new InflaterInputStream(fileInputStream);      	   
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = inputStream.read(buffer)) != -1; ) {
        	outputStream.write(buffer, 0, length);
        }
        
        try {
			result = outputStream.toString("UTF-8");
		} catch (UnsupportedEncodingException ex) {
            result = new String (outputStream.toString());
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
		}
        
        inputStream.close();
        
        return result;
	}

}
