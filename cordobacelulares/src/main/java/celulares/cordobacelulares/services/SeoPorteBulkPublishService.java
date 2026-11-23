package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.PorteBulkPublishDtos;
import celulares.cordobacelulares.exceptions.ApiConflictException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SeoPorteBulkPublishService {

    private final SeoProductContentService productContentService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SeoPorteBulkPublishService(SeoProductContentService productContentService) {
        this.productContentService = productContentService;
    }

    public PorteBulkPublishDtos.PreviewResponse preview() {
        return productContentService.porteBulkPublishPlan().preview();
    }

    public PorteBulkPublishDtos.PublishResponse publish() {
        if (!running.compareAndSet(false, true)) {
            throw new ApiConflictException("Ya hay una publicacion masiva de fichas de Porte en curso");
        }
        try {
            SeoProductContentService.PorteBulkPublishPlan plan = productContentService.porteBulkPublishPlan();
            int published = 0;
            int skippedDuringPublication = 0;
            List<PorteBulkPublishDtos.ErrorDetail> errors = new ArrayList<>();
            for (String slug : plan.eligibleSlugs()) {
                try {
                    if (productContentService.publishPorteCandidate(slug)) {
                        published++;
                    } else {
                        skippedDuringPublication++;
                    }
                } catch (RuntimeException ex) {
                    errors.add(new PorteBulkPublishDtos.ErrorDetail(slug, safeMessage(ex)));
                }
            }
            int initiallySkipped = plan.preview().totalEvaluated() - plan.eligibleSlugs().size();
            return new PorteBulkPublishDtos.PublishResponse(
                    plan.preview().totalEvaluated(),
                    published,
                    initiallySkipped + skippedDuringPublication,
                    errors.size(),
                    List.copyOf(errors)
            );
        } finally {
            running.set(false);
        }
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "No se pudo publicar el producto" : message;
    }
}
