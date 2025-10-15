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

    // A class to hold property name and type from the header
    private static class VertexProperty {
        String type;
        String name;

        VertexProperty(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

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

    // MODIFIED: This now stores VertexProperty objects instead of just Strings
    private List<VertexProperty> vertexProperties = new ArrayList<>();

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

    public ParseResult parse(InputStream inputStream) throws IOException {
        if (!parseHeader(inputStream)) {
            throw new IOException("Invalid PLY file format");
        }

        Log.d(TAG, String.format("PLY Info - Vertices: %d, Faces: %d, Format: %s",
                vertexCount, faceCount, isBinaryFormat ? "Binary" : "ASCII"));

        if (isBinaryFormat) {
            parseBinaryData(inputStream);
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            parseAsciiData(reader);
        }

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
     * MODIFIED: This now reads the property type (e.g., float, uchar) in addition to the name.
     */
    private boolean parseHeader(InputStream inputStream) throws IOException {
        String line = readLine(inputStream);
        if (line == null || !line.trim().equals("ply")) {
            return false;
        }

        String currentElement = null;

        while ((line = readLine(inputStream)) != null) {
            line = line.trim();
            if (line.startsWith("comment") || line.isEmpty()) continue;
            if (line.equals("end_header")) break;

            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) continue;

            String keyword = tokens[0];

            if (keyword.equals("format")) {
                if (tokens[1].equals("ascii")) isBinaryFormat = false;
                else if (tokens[1].equals("binary_little_endian")) {
                    isBinaryFormat = true;
                    isBigEndian = false;
                } else if (tokens[1].equals("binary_big_endian")) {
                    isBinaryFormat = true;
                    isBigEndian = true;
                }
            } else if (keyword.equals("element")) {
                currentElement = tokens[1];
                int count = Integer.parseInt(tokens[2]);
                if (currentElement.equals("vertex")) vertexCount = count;
                else if (currentElement.equals("face")) faceCount = count;
            } else if (keyword.equals("property") && "vertex".equals(currentElement)) {
                String type = tokens[1];
                String name = tokens[2];
                vertexProperties.add(new VertexProperty(type, name));

                if (name.equals("nx") || name.equals("normal_x")) hasNormals = true;
                if (name.equals("red") || name.equals("r") || name.equals("f_dc_0")) hasColors = true;
            }
        }
        return vertexCount > 0;
    }

    /**
     * REWRITTEN: This now dynamically reads data based on the property types found in the header.
     * This is the main fix for the color issue.
     */
    private void parseBinaryData(InputStream inputStream) throws IOException {
        vertices = new float[vertexCount * 3];
        if (hasColors) colors = new float[vertexCount * 3];
        if (hasNormals) normals = new float[vertexCount * 3];

        byte[] data = readAllBytes(inputStream);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(isBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < vertexCount; i++) {
            for (VertexProperty prop : vertexProperties) {
                // Read value based on the property type
                float val1 = 0, val2 = 0, val3 = 0;

                switch (prop.type) {
                    case "float":
                    case "float32":
                        val1 = buffer.getFloat();
                        break;
                    case "uchar":
                    case "uint8":
                        val1 = (buffer.get() & 0xFF) / 255.0f; // Normalize uchar to 0-1
                        break;
                    case "char":
                    case "int8":
                        val1 = buffer.get() / 255.0f;
                        break;
                    case "ushort":
                    case "uint16":
                        val1 = (buffer.getShort() & 0xFFFF) / 65535.0f;
                        break;
                    case "short":
                    case "int16":
                        val1 = buffer.getShort() / 65535.0f;
                        break;
                    // Add other types if needed, otherwise we skip
                    default:
                        continue;
                }

                // Assign value to the correct array based on property name
                switch (prop.name) {
                    case "x": vertices[i * 3] = val1; break;
                    case "y": vertices[i * 3 + 1] = val1; break;
                    case "z": vertices[i * 3 + 2] = val1; break;
                    case "nx": if(hasNormals) normals[i * 3] = val1; break;
                    case "ny": if(hasNormals) normals[i * 3 + 1] = val1; break;
                    case "nz": if(hasNormals) normals[i * 3 + 2] = val1; break;
                    // Handle common color property names
                    case "red":
                    case "r":
                    case "f_dc_0": // Common in Gaussian Splatting
                        if(hasColors) colors[i * 3] = val1;
                        break;
                    case "green":
                    case "g":
                    case "f_dc_1": // Common in Gaussian Splatting
                        if(hasColors) colors[i * 3 + 1] = val1;
                        break;
                    case "blue":
                    case "b":
                    case "f_dc_2": // Common in Gaussian Splatting
                        if(hasColors) colors[i * 3 + 2] = val1;
                        break;
                }
            }
        }

        // Face parsing remains the same as it's typically simple integers
        List<Integer> faceIndices = new ArrayList<>();
        int faceDataStart = buffer.position();
        buffer.position(faceDataStart);

        for (int i = 0; i < faceCount; i++) {
            if (buffer.remaining() < 1) break;
            int count = buffer.get() & 0xFF;
            if (buffer.remaining() < count * 4) break; // Assuming int indices

            if (count == 3) {
                faceIndices.add(buffer.getInt());
                faceIndices.add(buffer.getInt());
                faceIndices.add(buffer.getInt());
            } else if (count == 4) {
                int v0 = buffer.getInt(); int v1 = buffer.getInt(); int v2 = buffer.getInt(); int v3 = buffer.getInt();
                faceIndices.add(v0); faceIndices.add(v1); faceIndices.add(v2);
                faceIndices.add(v0); faceIndices.add(v2); faceIndices.add(v3);
            } else {
                for(int j=0; j<count; j++) buffer.getInt(); // Skip other face types
            }
        }
        indices = faceIndices.stream().mapToInt(i -> i).toArray();

        if (!hasColors) generateDefaultColors();
        if (!hasNormals) generateNormals();
    }


    private void parseAsciiData(BufferedReader reader) throws IOException {
        vertices = new float[vertexCount * 3];
        if (hasColors) colors = new float[vertexCount * 3];
        if (hasNormals) normals = new float[vertexCount * 3];

        for (int i = 0; i < vertexCount; i++) {
            String line = reader.readLine();
            if (line == null) break;
            String[] tokens = line.trim().split("\\s+");
            for (int j = 0; j < vertexProperties.size() && j < tokens.length; j++) {
                VertexProperty prop = vertexProperties.get(j);
                float value = Float.parseFloat(tokens[j]);

                switch (prop.name) {
                    case "x": vertices[i * 3] = value; break;
                    case "y": vertices[i * 3 + 1] = value; break;
                    case "z": vertices[i * 3 + 2] = value; break;
                    case "nx": if (hasNormals) normals[i * 3] = value; break;
                    case "ny": if (hasNormals) normals[i * 3 + 1] = value; break;
                    case "nz": if (hasNormals) normals[i * 3 + 2] = value; break;
                    case "red":
                    case "r":
                    case "f_dc_0":
                        if (hasColors) colors[i * 3] = "float".equals(prop.type) ? value : value / 255.0f;
                        break;
                    case "green":
                    case "g":
                    case "f_dc_1":
                        if (hasColors) colors[i * 3 + 1] = "float".equals(prop.type) ? value : value / 255.0f;
                        break;
                    case "blue":
                    case "b":
                    case "f_dc_2":
                        if (hasColors) colors[i * 3 + 2] = "float".equals(prop.type) ? value : value / 255.0f;
                        break;
                }
            }
        }

        List<Integer> faceIndices = new ArrayList<>();
        for (int i = 0; i < faceCount; i++) {
            String line = reader.readLine(); if (line == null) break;
            String[] tokens = line.trim().split("\\s+");
            int count = Integer.parseInt(tokens[0]);
            if (count == 3) {
                faceIndices.add(Integer.parseInt(tokens[1])); faceIndices.add(Integer.parseInt(tokens[2])); faceIndices.add(Integer.parseInt(tokens[3]));
            } else if (count == 4) {
                int v0 = Integer.parseInt(tokens[1]); int v1 = Integer.parseInt(tokens[2]); int v2 = Integer.parseInt(tokens[3]); int v3 = Integer.parseInt(tokens[4]);
                faceIndices.add(v0); faceIndices.add(v1); faceIndices.add(v2);
                faceIndices.add(v0); faceIndices.add(v2); faceIndices.add(v3);
            }
        }
        indices = faceIndices.stream().mapToInt(i -> i).toArray();

        if (!hasColors) generateDefaultColors();
        if (!hasNormals) generateNormals();
    }

    //
    // NO CHANGES NEEDED FOR METHODS BELOW THIS LINE
    //
    private void generateDefaultColors() {
        colors = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount * 3; i++) {
            colors[i] = 0.8f;
        }
        hasColors = true;
    }

    private void generateNormals() {
        normals = new float[vertexCount * 3];
        if (indices == null || indices.length == 0) return;

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i], i1 = indices[i+1], i2 = indices[i+2];
            float[] v0 = {vertices[i0*3], vertices[i0*3+1], vertices[i0*3+2]};
            float[] v1 = {vertices[i1*3], vertices[i1*3+1], vertices[i1*3+2]};
            float[] v2 = {vertices[i2*3], vertices[i2*3+1], vertices[i2*3+2]};
            float[] edge1 = {v1[0]-v0[0], v1[1]-v0[1], v1[2]-v0[2]};
            float[] edge2 = {v2[0]-v0[0], v2[1]-v0[1], v2[2]-v0[2]};
            float[] normal = cross(edge1, edge2);
            normals[i0*3] += normal[0]; normals[i0*3+1] += normal[1]; normals[i0*3+2] += normal[2];
            normals[i1*3] += normal[0]; normals[i1*3+1] += normal[1]; normals[i1*3+2] += normal[2];
            normals[i2*3] += normal[0]; normals[i2*3+1] += normal[1]; normals[i2*3+2] += normal[2];
        }

        for (int i = 0; i < vertexCount * 3; i+=3) {
            float len = (float)Math.sqrt(normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
            if (len > 0) { normals[i]/=len; normals[i+1]/=len; normals[i+2]/=len; }
        }
        hasNormals = true;
    }

    private float[] cross(float[] a, float[] b) {
        return new float[]{a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]};
    }

    private String readLine(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            if (c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString().trim();
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[16384]; int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}