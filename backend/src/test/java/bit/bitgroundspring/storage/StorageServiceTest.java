package bit.bitgroundspring.storage;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    private static final String PUBLIC_URL = "https://cdn.example.com";

    private S3Client s3Client;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storageService = new StorageService(s3Client,
                new StorageProperties("bucket", "ap-northeast-2", null, PUBLIC_URL, null, null));
    }

    @Test
    void 이미지를_UUID_키로_올리고_공개_URL을_반환한다() {
        String url = storageService.uploadImage("images", new MockMultipartFile("file", "../../evil.png", "image/png", new byte[]{1, 2}));

        assertThat(url).startsWith(PUBLIC_URL + "/images/").endsWith(".png").doesNotContain("evil");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 빈_파일_큰_파일_이미지가_아닌_파일은_거부한다() {
        assertError(new MockMultipartFile("file", "a.png", "image/png", new byte[0]), ErrorCode.FILE_EMPTY);
        assertError(new MockMultipartFile("file", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1]), ErrorCode.FILE_TOO_LARGE);
        assertError(new MockMultipartFile("file", "a.html", "text/html", "<script>".getBytes()), ErrorCode.UNSUPPORTED_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    void 직접_올린_파일만_삭제한다() {
        storageService.deleteByUrl(PUBLIC_URL + "/profile/2026/11/02/a.png");
        storageService.deleteByUrl("https://k.kakaocdn.net/profile.jpg"); // 소셜 프로필 이미지
        storageService.deleteByUrl(null);

        verify(s3Client, times(1)).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key("profile/2026/11/02/a.png").build());
        verifyNoMoreInteractions(s3Client);
    }

    @Test
    void 공개_URL을_지정하지_않으면_S3_기본_주소를_쓴다() {
        var props = new StorageProperties("bitground", "ap-northeast-2", null, null, null, null);
        assertThat(props.resolvedPublicUrl()).isEqualTo("https://bitground.s3.ap-northeast-2.amazonaws.com");

        var local = new StorageProperties("bitground", "ap-northeast-2", "http://localhost:9090/", null, null, null);
        assertThat(local.resolvedPublicUrl()).isEqualTo("http://localhost:9090/bitground");
    }

    private void assertError(MockMultipartFile file, ErrorCode code) {
        assertThatThrownBy(() -> storageService.uploadImage("images", file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }
}
