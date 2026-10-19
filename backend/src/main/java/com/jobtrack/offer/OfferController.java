package com.jobtrack.offer;

import com.jobtrack.auth.AppUserPrincipal;
import com.jobtrack.common.PageResponse;
import com.jobtrack.common.WorkMode;
import com.jobtrack.compatibility.CompatibilityModels.Result;
import com.jobtrack.offer.OfferDtos.ChangeStatusRequest;
import com.jobtrack.offer.OfferDtos.FavoriteRequest;
import com.jobtrack.offer.OfferDtos.HistoryEntry;
import com.jobtrack.offer.OfferDtos.OfferRequest;
import com.jobtrack.offer.OfferDtos.OfferResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/offers")
public class OfferController {

    private final OfferService service;
    private final OfferCsvService csv;

    public OfferController(OfferService service, OfferCsvService csv) {
        this.service = service;
        this.csv = csv;
    }

    @GetMapping
    public PageResponse<OfferResponse> list(@AuthenticationPrincipal AppUserPrincipal user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<OfferStatus> status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "detectedDate,desc") String sort) {
        var filter = new OfferSpecifications.Filter(q, status, company, location, workMode, favorite);
        return service.list(user.getId(), filter, page, size, sort);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse create(@AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody OfferRequest request) {
        return service.create(user.getId(), request);
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> exportCsv(@AuthenticationPrincipal AppUserPrincipal user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("jobtrack-offers.csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.export(user.getId()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OfferCsvService.ImportResult> importCsv(@AuthenticationPrincipal AppUserPrincipal user,
            @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
        OfferCsvService.ImportResult result = csv.importCsv(user.getId(), file.getBytes(), dryRun);
        boolean rejected = !dryRun && !result.errors().isEmpty();
        return ResponseEntity.status(rejected ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK).body(result);
    }

    @GetMapping("/{id}")
    public OfferResponse get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.get(user.getId(), id);
    }

    @PutMapping("/{id}")
    public OfferResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody OfferRequest request) {
        return service.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        service.delete(user.getId(), id);
    }

    @PatchMapping("/{id}/favorite")
    public OfferResponse favorite(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody FavoriteRequest request) {
        return service.setFavorite(user.getId(), id, request.favorite());
    }

    @PostMapping("/{id}/status")
    public OfferResponse changeStatus(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(user.getId(), id, request);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.history(user.getId(), id);
    }

    @GetMapping("/{id}/compatibility")
    public Result compatibility(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return service.compatibility(user.getId(), id);
    }
}
