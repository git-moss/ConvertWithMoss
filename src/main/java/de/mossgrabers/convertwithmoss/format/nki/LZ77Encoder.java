package de.mossgrabers.convertwithmoss.format.nki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StreamTokenizer;


public class LZ77Encoder
{
    public static final int DEFAULT_BUFF_SIZE = 1024;

    protected int           mBufferSize;
    protected Reader        mIn;
    protected PrintWriter   mOut;
    protected StringBuffer  mSearchBuffer;


    public LZ77Encoder ()
    {
        this (DEFAULT_BUFF_SIZE);
    }


    public LZ77Encoder (final int buffSize)
    {
        this.mBufferSize = buffSize;
        this.mSearchBuffer = new StringBuffer (this.mBufferSize);
    }


    private void trimSearchBuffer ()
    {
        if (this.mSearchBuffer.length () > this.mBufferSize)
        {
            this.mSearchBuffer = this.mSearchBuffer.delete (0, this.mSearchBuffer.length () - this.mBufferSize);
        }
    }


    /**
     * Uncompress method
     *
     * @param infile the name of the file to uncompress - automatically appends a ".lz77" extension
     *            to the supplied filename
     * @exception IOException if an error occurs during file processing
     */
    public void unCompress (final byte [] data) throws IOException
    {
        this.mIn = new BufferedReader (new InputStreamReader (new ByteArrayInputStream (data)));
        final StreamTokenizer st = new StreamTokenizer (this.mIn);

        st.ordinaryChar (' ');
        st.ordinaryChar ('.');
        st.ordinaryChar ('-');
        st.ordinaryChar ('\n');
        st.wordChars ('\n', '\n');
        st.wordChars (' ', '}');

        int offset, length;
        while (st.nextToken () != StreamTokenizer.TT_EOF)
        {
            switch (st.ttype)
            {
                case StreamTokenizer.TT_WORD:
                    this.mSearchBuffer.append (st.sval);
                    System.out.print (st.sval);
                    // Adjust search buffer size if necessary
                    this.trimSearchBuffer ();
                    break;
                case StreamTokenizer.TT_NUMBER:
                    offset = (int) st.nval; // set the offset
                    st.nextToken (); // get the separator (hopefully)
                    if (st.ttype == StreamTokenizer.TT_WORD)
                    {
                        // we got a word instead of the separator,
                        // therefore the first number read was actually part of a word
                        this.mSearchBuffer.append (offset + st.sval);
                        System.out.print (offset + st.sval);
                        break; // break out of the switch
                    }
                    // if we got this far then we must be reading a
                    // substitution pointer
                    st.nextToken (); // get the length
                    length = (int) st.nval;
                    // output substring from search buffer
                    final String output = this.mSearchBuffer.substring (offset, offset + length);
                    System.out.print (output);
                    this.mSearchBuffer.append (output);
                    // Adjust search buffer size if necessary
                    this.trimSearchBuffer ();
                    break;
                default:
                    // consume a '~'
            }
        }
        this.mIn.close ();
    }


    /**
     * Compress method
     *
     * @param infile the name of the file to compress. Automatically appends a ".lz77" extension to
     *            infile name when creating the output file
     * @exception IOException if an error occurs
     */
    public void compress (final String infile) throws IOException
    {
        // set up input and output
        this.mIn = new BufferedReader (new FileReader (infile));
        this.mOut = new PrintWriter (new BufferedWriter (new FileWriter (infile + ".lz77")));

        int nextChar;
        String currentMatch = "";
        int matchIndex = 0, tempIndex = 0;

        // while there are more characters - read a character
        while ((nextChar = this.mIn.read ()) != -1)
        {
            // look in our search buffer for a match
            tempIndex = this.mSearchBuffer.indexOf (currentMatch + (char) nextChar);
            // if match then append nextChar to currentMatch
            // and update index of match
            if (tempIndex != -1)
            {
                currentMatch += (char) nextChar;
                matchIndex = tempIndex;
            }
            else
            {
                // found longest match, now should we encode it?
                final String codedString = "~" + matchIndex + "~" + currentMatch.length () + "~" + (char) nextChar;
                final String concat = currentMatch + (char) nextChar;
                // is coded string shorter than raw text?
                if (codedString.length () <= concat.length ())
                {
                    this.mOut.print (codedString);
                    this.mSearchBuffer.append (concat); // append to the search buffer
                    currentMatch = "";
                    matchIndex = 0;
                }
                else
                {
                    // otherwise, output chars one at a time from
                    // currentMatch until we find a new match or
                    // run out of chars
                    currentMatch = concat;
                    matchIndex = -1;
                    while (currentMatch.length () > 1 && matchIndex == -1)
                    {
                        this.mOut.print (currentMatch.charAt (0));
                        this.mSearchBuffer.append (currentMatch.charAt (0));
                        currentMatch = currentMatch.substring (1, currentMatch.length ());
                        matchIndex = this.mSearchBuffer.indexOf (currentMatch);
                    }
                }
                // Adjust search buffer size if necessary
                this.trimSearchBuffer ();
            }
        }
        // flush any match we may have had when EOF encountered
        if (matchIndex != -1)
        {
            final String codedString = "~" + matchIndex + "~" + currentMatch.length ();
            if (codedString.length () <= currentMatch.length ())
            {
                this.mOut.print ("~" + matchIndex + "~" + currentMatch.length ());
            }
            else
            {
                this.mOut.print (currentMatch);
            }
        }
        // close files
        this.mIn.close ();
        this.mOut.flush ();
        this.mOut.close ();
    }
}