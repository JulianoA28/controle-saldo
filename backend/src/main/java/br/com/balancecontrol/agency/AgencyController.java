package br.com.balancecontrol.agency;

import br.com.balancecontrol.common.web.PageResponse;
import br.com.balancecontrol.common.web.SearchPattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agencies")
public class AgencyController {

    private final AgencyRepository agencies;

    public AgencyController(AgencyRepository agencies) {
        this.agencies = agencies;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AgencyResponse> list(@RequestParam(required = false) String search, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("acronym", "code"));
        return PageResponse.of(agencies.search(SearchPattern.contains(search), sorted), AgencyResponse::from);
    }
}
