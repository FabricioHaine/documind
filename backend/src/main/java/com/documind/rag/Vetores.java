package com.documind.rag;

/** Conversao float[] -> literal de texto do pgvector ("[a,b,c]"). */
public final class Vetores {

    private Vetores() {
    }

    public static String paraLiteral(float[] vetor) {
        if (vetor == null || vetor.length == 0) {
            throw new IllegalArgumentException("Vetor vazio.");
        }
        StringBuilder literal = new StringBuilder(vetor.length * 8 + 2);
        literal.append('[');
        for (float componente : vetor) {
            literal.append(componente).append(',');
        }
        literal.setLength(literal.length() - 1);
        literal.append(']');
        return literal.toString();
    }
}
