package br.com.balancecontrol.dataload;

import br.com.balancecontrol.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Legacy load report: what was loaded, what was quarantined and why, and balance divergences. */
@RestController
@RequestMapping("/dataload")
public class DataloadController {

    private final DataloadQueries queries;

    public DataloadController(DataloadQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/summary")
    public DataloadSummary summary() {
        return queries.summary();
    }

    @GetMapping("/rejections")
    public PageResponse<RejectionRow> rejections(@RequestParam(required = false) String source,
                                                 @RequestParam(required = false) String reason,
                                                 Pageable pageable) {
        return queries.rejections(source, reason, pageable);
    }

    @GetMapping("/divergences")
    public PageResponse<DivergenceRow> divergences(@RequestParam(defaultValue = "false") boolean onlyNegative,
                                                   Pageable pageable) {
        return queries.divergences(onlyNegative, pageable);
    }
}
