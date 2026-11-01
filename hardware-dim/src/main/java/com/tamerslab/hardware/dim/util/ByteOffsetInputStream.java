package com.tamerslab.hardware.dim.util;

import java.io.IOException;

public interface ByteOffsetInputStream {
    byte[] readNBytes(int numberOfBytes) throws IOException;

    byte[] readToOffset(int offset) throws IOException;
}
