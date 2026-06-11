package p5laris.common.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector에 저장할 임베딩 벡터(Embedding Vector)의 검증, 정규화(L2 Normalization), 및 
 * 데이터베이스 저장용 문자열 리터럴 변환을 제공하는 공통 벡터 유틸리티 클래스입니다.
 * 
 * Gemini 임베딩 모델의 차원을 변경(축소)하여 사용할 때 Cosine 유사도 기반 검색의 신뢰도와 품질을 확보하기 위해,
 * 저장 및 쿼리 전 정규화를 통일성 있게 수행하도록 `ai` 모듈과 `mission` 모듈의 중복 유틸리티를 `:common` 모듈로 단일화하였습니다.
 */
public final class EmbeddingVectorUtils {

    private EmbeddingVectorUtils() {
    }

    public static List<Float> normalize(List<Float> values, int expectedDimension) {
        if (values == null || values.size() != expectedDimension) {
            throw new IllegalArgumentException("embedding dimension mismatch");
        }

        double normSquared = 0.0d;
        for (Float value : values) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding contains invalid value");
            }
            normSquared += value * value;
        }

        double norm = Math.sqrt(normSquared);
        if (norm == 0.0d) {
            throw new IllegalArgumentException("embedding norm is zero");
        }

        List<Float> normalized = new ArrayList<>(values.size());
        for (Float value : values) {
            normalized.add((float) (value / norm));
        }
        return normalized;
    }

    public static String toPgVectorLiteral(List<Float> values) {
        StringBuilder builder = new StringBuilder(values.size() * 8);
        builder.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values.get(index)));
        }
        builder.append(']');
        return builder.toString();
    }
}
