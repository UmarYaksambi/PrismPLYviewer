package com.prism.plyviewer360;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Professional PLY file parser supporting both ASCII and binary formats
 * Handles vertex positions, colors, normals, and face indices
 */
public class PLYParser {
    private static final String TAG = "PLYParser";

    // Parsed data structures
    private float[] vertices;
    private float[] colors;
    private float[] normals;
    private int[] indices;

    private boolean hasColors = false;
    private boolean hasNormals = false;

    // Header information
    private int vertexCount = 0;
    private int faceCount = 0;
    private boolean isBinaryFormat = false;
    private boolean isBigEndian = false;

    // Property information
    private List<String> vertexProperties = new ArrayList<>();

    public static class ParseResult {
        public float[] vertices;
        public float[] colors;
        public float[] normals;
        public int[] indices;
        public boolean hasColors;
        public boolean hasNormals;

        public boolean isValid() {
            return vertices != null && vertices.length > 0;
        }
    }

    /**
     * Parse PLY file from InputStream
     */
    public ParseResult parse(InputStream inputStream) throws IOException {
        // Parse header
        if (!parseHeader(inputStream)) {
            throw new IOException("Invalid PLY file format");
        }

        Log.d(TAG, String.format("PLY Info - Vertices: %d, Faces: %d, Format: %s",
                vertexCount, faceCount, isBinaryFormat ? "Binary" : "ASCII"));

        // Parse data based on format
        if (isBinaryFormat) {
            parseBinaryData(inputStream);
        } else {
            // For ASCII, we can now safely use a BufferedReader for the rest of the file
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            parseAsciiData(reader);
        }

        // Build result
        ParseResult result = new ParseResult();
        result.vertices = vertices;
        result.colors = colors;
        result.normals = normals;
        result.indices = indices;
        result.hasColors = hasColors;
        result.hasNormals = hasNormals;

        return result;
    }

    /**
     * Parse PLY header to extract format and property information
     */
    private boolean parseHeader(InputStream inputStream) throws IOException {
        String line = readLine(inputStream);
        if (line == null || !line.trim().equals("ply")) {
            return false;
        }

        String currentElement = null;

        while ((line = readLine(inputStream)) != null) {
            line = line.trim();

            if (line.startsWith("comment") || line.isEmpty()) {
                continue;
            }

            if (line.equals("end_header")) {
                break;
            }

            String[] tokens = line.split("\\s+");

            if (tokens[0].equals("format")) {
                if (tokens[1].equals("ascii")) {
                    isBinaryFormat = false;
                } else if (tokens[1].equals("binary_little_endian")) {
                    isBinaryFormat = true;
                    isBigEndian = false;
                } else if (tokens[1].equals("binary_big_endian")) {
                    isBinaryFormat = true;
                    isBigEndian = true;
                }
            } else if (tokens[0].equals("element")) {
                currentElement = tokens[1];
                int count = Integer.parseInt(tokens[2]);

                if (currentElement.equals("vertex")) {
                    vertexCount = count;
                } else if (currentElement.equals("face")) {
                    faceCount = count;
                }
            } else if (tokens[0].equals("property") && currentElement != null) {
                if (currentElement.equals("vertex")) {
                    String propertyName = tokens[tokens.length - 1];
                    vertexProperties.add(propertyName);

                    // Check for specific properties
                    if (propertyName.equals("nx") || propertyName.equals("normal_x")) {
                        hasNormals = true;
                    }
                    if (propertyName.equals("red") || propertyName.equals("r")) {
                        hasColors = true;
                    }
                }
            }
        }

        return vertexCount > 0;
    }

    /**
     * Parse ASCII format PLY data
     */
    private void parseAsciiData(BufferedReader reader) throws IOException {
        // Initialize arrays
        vertices = new float[vertexCount * 3];
        if (hasColors) colors = new float[vertexCount * 3];
        if (hasNormals) normals = new float[vertexCount * 3];

        List<Integer> faceIndices = new ArrayList<>();

        // Parse vertices
        for (int i = 0; i < vertexCount; i++) {
            String line = reader.readLine();
            if (line == null) break;

            String[] tokens = line.trim().split("\\s+");
            int tokenIndex = 0;

            // Parse based on property order
            for (String prop : vertexProperties) {
                if (tokenIndex >= tokens.length) break;

                float value = Float.parseFloat(tokens[tokenIndex]);

                if (prop.equals("x")) {
                    vertices[i * 3] = value;
                } else if (prop.equals("y")) {
                    vertices[i * 3 + 1] = value;
                } else if (prop.equals("z")) {
                    vertices[i * 3 + 2] = value;
                } else if (hasNormals && prop.equals("nx")) {
                    normals[i * 3] = value;
                } else if (hasNormals && prop.equals("ny")) {
                    normals[i * 3 + 1] = value;
                } else if (hasNormals && prop.equals("nz")) {
                    normals[i * 3 + 2] = value;
                } else if (hasColors && (prop.equals("red") || prop.equals("r"))) {
                    colors[i * 3] = value / 255.0f;
                } else if (hasColors && (prop.equals("green") || prop.equals("g"))) {
                    colors[i * 3 + 1] = value / 255.0f;
                } else if (hasColors && (prop.equals("blue") || prop.equals("b"))) {
                    colors[i * 3 + 2] = value / 255.0f;
                }

                tokenIndex++;
            }
        }

        // Parse faces
        for (int i = 0; i < faceCount; i++) {
            String line = reader.readLine();
            if (line == null) break;

            String[] tokens = line.trim().split("\\s+");
            int vertexCountInFace = Integer.parseInt(tokens[0]);

            if (vertexCountInFace == 3) {
                // Triangle
                faceIndices.add(Integer.parseInt(tokens[1]));
                faceIndices.add(Integer.parseInt(tokens[2]));
                faceIndices.add(Integer.parseInt(tokens[3]));
            } else if (vertexCountInFace == 4) {
                // Quad - triangulate
                int v0 = Integer.parseInt(tokens[1]);
                int v1 = Integer.parseInt(tokens[2]);
                int v2 = Integer.parseInt(tokens[3]);
                int v3 = Integer.parseInt(tokens[4]);

                faceIndices.add(v0);
                faceIndices.add(v1);
                faceIndices.add(v2);

                faceIndices.add(v0);
                faceIndices.add(v2);
                faceIndices.add(v3);
            }
        }

        // Convert indices to array
        indices = new int[faceIndices.size()];
        for (int i = 0; i < faceIndices.size(); i++) {
            indices[i] = faceIndices.get(i);
        }

        // Generate default colors if not present
        if (!hasColors) {
            generateDefaultColors();
        }

        // Generate normals if not present
        if (!hasNormals) {
            generateNormals();
        }
    }

    /**
     * Parse binary format PLY data
     */
    private void parseBinaryData(InputStream inputStream) throws IOException {
        // Read all remaining bytes
        byte[] data = readAllBytes(inputStream);
        ByteBuffer buffer = ByteBuffer.wrap(data);

        if (isBigEndian) {
            buffer.order(ByteOrder.BIG_ENDIAN);
        } else {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        // Initialize arrays
        vertices = new float[vertexCount * 3];
        if (hasColors) colors = new float[vertexCount * 3];
        if (hasNormals) normals = new float[vertexCount * 3];

        // Parse vertices
        for (int i = 0; i < vertexCount; i++) {
            for (String prop : vertexProperties) {
                if (prop.equals("x")) {
                    vertices[i * 3] = buffer.getFloat();
                } else if (prop.equals("y")) {
                    vertices[i * 3 + 1] = buffer.getFloat();
                } else if (prop.equals("z")) {
                    vertices[i * 3 + 2] = buffer.getFloat();
                } else if (hasNormals && prop.equals("nx")) {
                    normals[i * 3] = buffer.getFloat();
                } else if (hasNormals && prop.equals("ny")) {
                    normals[i * 3 + 1] = buffer.getFloat();
                } else if (hasNormals && prop.equals("nz")) {
                    normals[i * 3 + 2] = buffer.getFloat();
                } else if (hasColors && (prop.equals("red") || prop.equals("r"))) {
                    colors[i * 3] = (buffer.get() & 0xFF) / 255.0f;
                } else if (hasColors && (prop.equals("green") || prop.equals("g"))) {
                    colors[i * 3 + 1] = (buffer.get() & 0xFF) / 255.0f;
                } else if (hasColors && (prop.equals("blue") || prop.equals("b"))) {
                    colors[i * 3 + 2] = (buffer.get() & 0xFF) / 255.0f;
                } else {
                    // Skip unknown property (assume float)
                    buffer.getFloat();
                }
            }
        }

        // Parse faces
        List<Integer> faceIndices = new ArrayList<>();
        for (int i = 0; i < faceCount; i++) {
            int vertexCountInFace = buffer.get() & 0xFF;

            if (vertexCountInFace == 3) {
                faceIndices.add(buffer.getInt());
                faceIndices.add(buffer.getInt());
                faceIndices.add(buffer.getInt());
            } else if (vertexCountInFace == 4) {
                int v0 = buffer.getInt();
                int v1 = buffer.getInt();
                int v2 = buffer.getInt();
                int v3 = buffer.getInt();

                faceIndices.add(v0);
                faceIndices.add(v1);
                faceIndices.add(v2);
                faceIndices.add(v0);
                faceIndices.add(v2);
                faceIndices.add(v3);
            }
        }

        indices = new int[faceIndices.size()];
        for (int i = 0; i < faceIndices.size(); i++) {
            indices[i] = faceIndices.get(i);
        }

        if (!hasColors) generateDefaultColors();
        if (!hasNormals) generateNormals();
    }

    /**
     * Generate default white colors for vertices
     */
    private void generateDefaultColors() {
        colors = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount * 3; i++) {
            colors[i] = 0.8f; // Light gray
        }
        hasColors = true;
    }

    /**
     * Generate per-face normals
     */
    private void generateNormals() {
        normals = new float[vertexCount * 3];
        float[] normalCounts = new float[vertexCount];

        // Calculate face normals and accumulate to vertices
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            float[] v0 = {vertices[i0 * 3], vertices[i0 * 3 + 1], vertices[i0 * 3 + 2]};
            float[] v1 = {vertices[i1 * 3], vertices[i1 * 3 + 1], vertices[i1 * 3 + 2]};
            float[] v2 = {vertices[i2 * 3], vertices[i2 * 3 + 1], vertices[i2 * 3 + 2]};

            float[] edge1 = {v1[0] - v0[0], v1[1] - v0[1], v1[2] - v0[2]};
            float[] edge2 = {v2[0] - v0[0], v2[1] - v0[1], v2[2] - v0[2]};

            float[] normal = cross(edge1, edge2);

            for (int j = 0; j < 3; j++) {
                int idx = indices[i + j];
                normals[idx * 3] += normal[0];
                normals[idx * 3 + 1] += normal[1];
                normals[idx * 3 + 2] += normal[2];
                normalCounts[idx]++;
            }
        }

        // Normalize
        for (int i = 0; i < vertexCount; i++) {
            if (normalCounts[i] > 0) {
                float length = (float) Math.sqrt(
                        normals[i * 3] * normals[i * 3] +
                                normals[i * 3 + 1] * normals[i * 3 + 1] +
                                normals[i * 3 + 2] * normals[i * 3 + 2]
                );
                if (length > 0) {
                    normals[i * 3] /= length;
                    normals[i * 3 + 1] /= length;
                    normals[i * 3 + 2] /= length;
                }
            }
        }

        hasNormals = true;
    }

    private float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    /**
     * Reads a single line of ASCII text from an InputStream.
     */
    private String readLine(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            if (c == '\n') {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString().trim();
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384]; // Read in 16KB chunks

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }
}