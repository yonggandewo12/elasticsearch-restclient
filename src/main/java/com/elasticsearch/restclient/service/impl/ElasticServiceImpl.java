package com.elasticsearch.restclient.service.impl;

import com.elasticsearch.restclient.constants.KeyConstant;
import com.elasticsearch.restclient.entity.Book;
import com.elasticsearch.restclient.service.ElasticService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.support.IncludeExclude;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.tophits.ParsedTopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.profile.ProfileShardResult;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @Description
 * @ClassName BookServiceImpl
 * @Author xuliang
 * @date 2020.04.17 17:30
 */
@Service
@Slf4j
public class ElasticServiceImpl implements ElasticService {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    @Override
    public IndexResponse addBook(Book book) {
        IndexRequest indexRequest = new IndexRequest(KeyConstant.BOOK_INDEX);
        indexRequest.type(KeyConstant.BOOK_TYPE);
        indexRequest.create(true);
        indexRequest.source(convertBookToMap(book));
        if (book.getId() == null || "".equals(book.getId())) {
            //id非空校验
            indexRequest.id(UUID.randomUUID().toString());
        }else{
            indexRequest.id(book.getId());
        }
        IndexResponse response = new IndexResponse();
        try {
            response = restHighLevelClient.index(indexRequest);
        } catch (Exception e) {
            log.error("....ad book failed,message={}", e);
        }
        return response;

    }

    @Override
    public DeleteResponse deleteBook(String name) {
        SearchHits searchHits = getBook(name);
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(KeyConstant.BOOK_INDEX);
        deleteRequest.type(KeyConstant.BOOK_TYPE);
        DeleteResponse deleteResponse = new DeleteResponse();
        for (SearchHit hit : searchHits.getHits()) {
            deleteRequest.id(hit.getId());
            try {
                deleteResponse = restHighLevelClient.delete(deleteRequest);
            } catch (Exception e) {
                log.error("......delete book failed:{}", e);
                return deleteResponse;
            }
        }
        return deleteResponse;
    }

    @Override
    public SearchHits getBook(String name) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchPhraseQuery("name", name));
        searchRequest.indices(KeyConstant.BOOK_INDEX);
        searchRequest.types(KeyConstant.BOOK_TYPE);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("......get book error:{}", e);
        }
        return searchResponse.getHits();
    }

    @Override
    public UpdateResponse updateBook(Book book) {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.id(book.getId());
        updateRequest.type(KeyConstant.BOOK_TYPE);
        updateRequest.index(KeyConstant.BOOK_INDEX);
        updateRequest.doc(convertBookToMap(book));
        UpdateResponse updateResponse = new UpdateResponse();
        try {
            updateResponse = restHighLevelClient.update(updateRequest);
        } catch (Exception e) {
            log.info("........update book failed:{}", e);
        }
        return updateResponse;
    }

    /**
     * book to map
     * @param book
     * @return
     */
    public Map convertBookToMap(Book book) {
        Map map = new HashMap();
        if (book.getAuthor() != null) {
            map.put("author", book.getAuthor());
        }
        if (book.getInfo() != null) {
            map.put("info", book.getInfo());
        }
        if (book.getName() != null) {
            map.put("name", book.getName());
        }
        if (book.getPrice() != null) {
            map.put("price", book.getPrice());
        }
        if (book.getPublish() != null) {
            map.put("publish", book.getPublish());
        }
        if (book.getType() != null) {
            map.put("type", book.getType());
        }
        return map;
    }

    @Override
    public Max averageAggregate(String key) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.max("max").field(key));
        searchRequest.source(searchSourceBuilder);
        searchRequest.types(KeyConstant.BOOK_TYPE);
        searchRequest.indices(KeyConstant.BOOK_INDEX);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.info(".....聚合索引matrix失败：{}", e);
        }
        return searchResponse.getAggregations().get("max");
    }

    @Override
    public ParsedTopHits topAggregate(String key) {
        String[] includes = new String[2];
        includes[0] = "name";
        includes[1] = "price";
        String[] excludes = new String[4];
        excludes[0] = "publish";
        excludes[1] = "type";
        excludes[2] = "author";
        excludes[3] = "info";
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.topHits("top_sales_hits").sort(key, SortOrder.DESC).fetchSource(includes, excludes).size(2));
        searchRequest.source(searchSourceBuilder);
        searchRequest.types(KeyConstant.BOOK_TYPE);
        searchRequest.indices(KeyConstant.BOOK_INDEX);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.info(".....聚合索引matrix失败：{}", e);
        }
        return searchResponse.getAggregations().get("top_sales_hits");
    }

    @Override
    public ParsedLongTerms  termsAggregate(String key) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.terms("term").field(key));
        searchRequest.source(searchSourceBuilder);
        searchRequest.indices(KeyConstant.BOOK_INDEX);
        searchRequest.types(KeyConstant.BOOK_TYPE);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("......terms aggregate failed:{}", e);
        }
        return searchResponse.getAggregations().get("term");
    }

    @Override
    public ParsedLongTerms  logTermsMultiAggregate(String key) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.aggregation(AggregationBuilders.terms("bytes").field(key).order(Terms.Order.aggregation("ram.avg", false)).subAggregation(AggregationBuilders.stats("ram").field("machine.ram")));
        searchRequest.source(searchSourceBuilder);
        searchRequest.indices(KeyConstant.LOG_INDEX);
        searchRequest.types(KeyConstant.LOG_TYPE);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("......terms aggregate failed:{}", e);
        }
        return searchResponse.getAggregations().get("bytes");
    }

    @Override
    public ParsedStringTerms filterAggregate(String include, String exclude, String field) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        IncludeExclude includeExclude = new IncludeExclude(include, exclude);
        // 对text聚合索引时会报错，此时需要在field处指定其为keyword类型
        //searchSourceBuilder.aggregation(AggregationBuilders.terms("filter").includeExclude(includeExclude).field(field+".keyword"));
        searchSourceBuilder.aggregation(AggregationBuilders.terms("filter").includeExclude(includeExclude).field(field));
        searchRequest.source(searchSourceBuilder);
        searchRequest.indices(KeyConstant.LOG_INDEX);
        searchRequest.types(KeyConstant.LOG_TYPE);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("......terms aggregate failed:{}", e);
        }
        return searchResponse.getAggregations().get("filter");
    }

    @Override
    public SearchResponse term(String key) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //term是低级索引，是精确索引，但当字段为text是是要分词的，此时用term搜不出来，需要加附加字段.keyword
        searchSourceBuilder.query(QueryBuilders.constantScoreQuery(QueryBuilders.termQuery("articleID.keyword", key)));
        searchRequest.types(KeyConstant.FORM_TYPE);
        searchRequest.indices(KeyConstant.FORM_INDEX);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("....term query failed:{}", e);
        }
        return searchResponse;
    }

    @Override
    public SearchHits boolWithMultiFilter(String postDate, String articleId, String notKey) {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //term是低级索引，是精确索引，但当字段为text是是要分词的，此时用term搜不出来，需要加附加字段.keyword
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.should(QueryBuilders.termsQuery("postDate.keyword",postDate));
        boolQueryBuilder.should(QueryBuilders.termQuery("articleID.keyword",articleId));
        boolQueryBuilder.mustNot(QueryBuilders.termQuery("postDate.keyword", notKey));
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.types(KeyConstant.FORM_TYPE);
        searchRequest.indices(KeyConstant.FORM_INDEX);
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = new SearchResponse();
        try {
            searchResponse = restHighLevelClient.search(searchRequest);
        } catch (Exception e) {
            log.error("....term query failed:{}", e);
        }
        return searchResponse.getHits();
    }

}
