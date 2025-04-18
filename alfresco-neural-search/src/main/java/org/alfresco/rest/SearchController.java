package org.alfresco.rest;

import org.alfresco.opensearch.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller class for handling search requests.
 */
@RestController
@RequestMapping("/search")
public class SearchController {
    private static final Logger LOG = LoggerFactory.getLogger(SearchController.class);

    private final Search search;

    @Autowired
    public SearchController(Search search) {
        this.search = search;
    }

    @GetMapping
    public List<DocumentBean> search(
            @RequestParam String query, 
            @RequestParam(defaultValue = "neural") String searchType) throws Exception {

        // For now, just use the existing search method
        return search.search(query, searchType);
    }
}