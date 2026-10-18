package com.farmguardian.farmguardian.config.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Refresh Token 원문 해시 테스트")
class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @ParameterizedTest(name = "UTF-8 SHA-256 입력: {0}")
    @CsvSource({
            "abc, ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "팜가디언, 3c133ead4bfbdb581c8f7efb4ed412e3f7fb9e4e779dcbdbddad3b95f775650b"
    })
    @DisplayName("ASCII와 한글 입력의 UTF-8 SHA-256 해시는 알려진 결과와 일치한다")
    void matchesKnownUtf8Sha256Vectors(String input, String expectedHash) {
        assertThat(hasher.hash(input)).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("동일한 입력은 같은 해시이고 다른 입력은 다른 해시이다")
    void isDeterministicAndDistinguishesInputs() {
        assertAll(
                () -> assertThat(hasher.hash("header.payload.signature"))
                        .isEqualTo(hasher.hash("header.payload.signature")),
                () -> assertThat(hasher.hash("header.payload.signature"))
                        .isNotEqualTo(hasher.hash("header.payload.other-signature"))
        );
    }

    @Test
    @DisplayName("DB 저장 규격인 소문자 16진수 64자만 반환한다")
    void returnsLowercaseHexWithFixedLength() {
        assertThat(hasher.hash("header.payload.signature"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("원문을 trim하거나 대소문자 정규화하지 않는다")
    void preservesExactInput() {
        assertAll(
                () -> assertThat(hasher.hash("abc ")).isNotEqualTo(hasher.hash("abc")),
                () -> assertThat(hasher.hash("ABC")).isNotEqualTo(hasher.hash("abc"))
        );
    }

    @Test
    @DisplayName("null·빈 문자열·공백 입력을 거부한다")
    void rejectsMissingInput() {
        assertAll(
                () -> assertThatThrownBy(() -> hasher.hash(null))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> hasher.hash(""))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> hasher.hash(" \t\n"))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @Test
    @DisplayName("동일한 Hasher 인스턴스를 병렬 호출해도 해시 결과가 섞이지 않는다")
    void supportsConcurrentCalls() {
        IntStream.range(0, 100).parallel().forEach(index -> {
            assertThat(hasher.hash("abc"))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
            assertThat(hasher.hash("팜가디언"))
                    .isEqualTo("3c133ead4bfbdb581c8f7efb4ed412e3f7fb9e4e779dcbdbddad3b95f775650b");
        });
    }
}
