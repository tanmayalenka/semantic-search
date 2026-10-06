package com.example.contracts.embedding;

/**
 * Converts between float[] and pgvector's text literal format.
 *
 * pgvector accepts '[0.1,0.2,0.3]' via CAST(? AS vector). We do NOT
 * L2-normalize here — nomic-embed-text already returns normalized
 * vectors, and pgvector's cosine operator (<=>) normalizes internally.
 * Normalizing again would be a no-op at best and a subtle bug at worst
 * if you later swap models that don't normalize.
 */
public final class VectorCodec {

    private VectorCodec() {}

    public static String toLiteral(float[] vector) {
        var sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}