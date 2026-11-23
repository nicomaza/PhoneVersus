package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.PorteBulkPublishDtos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoPorteBulkPublishServiceTest {

    private final SeoProductContentService productContentService = mock(SeoProductContentService.class);
    private final SeoPorteBulkPublishService service = new SeoPorteBulkPublishService(productContentService);

    @Test
    void individualFailureDoesNotStopTheRemainingPublications() {
        PorteBulkPublishDtos.PreviewResponse preview = new PorteBulkPublishDtos.PreviewResponse(3, 1, 1, 0, 0);
        when(productContentService.porteBulkPublishPlan()).thenReturn(
                new SeoProductContentService.PorteBulkPublishPlan(preview, List.of("first", "broken", "last"))
        );
        when(productContentService.publishPorteCandidate("first")).thenReturn(true);
        when(productContentService.publishPorteCandidate("broken")).thenThrow(new IllegalStateException("fallo"));
        when(productContentService.publishPorteCandidate("last")).thenReturn(true);

        PorteBulkPublishDtos.PublishResponse result = service.publish();

        assertThat(result.totalEvaluated()).isEqualTo(5);
        assertThat(result.published()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.errorDetails()).containsExactly(
                new PorteBulkPublishDtos.ErrorDetail("broken", "fallo")
        );
    }

    @Test
    void repeatedExecutionDoesNotPublishTheSameProductAgain() {
        PorteBulkPublishDtos.PreviewResponse pending = new PorteBulkPublishDtos.PreviewResponse(1, 0, 0, 0, 0);
        PorteBulkPublishDtos.PreviewResponse alreadyPublished = new PorteBulkPublishDtos.PreviewResponse(0, 1, 0, 0, 0);
        when(productContentService.porteBulkPublishPlan()).thenReturn(
                new SeoProductContentService.PorteBulkPublishPlan(pending, List.of("phone")),
                new SeoProductContentService.PorteBulkPublishPlan(alreadyPublished, List.of())
        );
        when(productContentService.publishPorteCandidate("phone")).thenReturn(true);

        PorteBulkPublishDtos.PublishResponse first = service.publish();
        PorteBulkPublishDtos.PublishResponse second = service.publish();

        assertThat(first.published()).isEqualTo(1);
        assertThat(second.published()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
    }
}
