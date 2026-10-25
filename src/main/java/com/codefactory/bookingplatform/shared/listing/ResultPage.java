package com.codefactory.bookingplatform.shared.listing;

import java.util.List;
import java.util.function.Function;

/** A page of a listing and how many rows match in total, for the client to paginate (HU-003). */
public record ResultPage<T>(List<T> items, long total, int page, int size) {

    public ResultPage {
        items = List.copyOf(items);
    }

    public <R> ResultPage<R> map(Function<T, R> mapper) {
        return new ResultPage<>(items.stream().map(mapper).toList(), total, page, size);
    }
}
