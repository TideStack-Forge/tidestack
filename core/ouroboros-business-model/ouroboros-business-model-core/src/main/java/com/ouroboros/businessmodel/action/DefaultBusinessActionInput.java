package com.ouroboros.businessmodel.action;

/** Reusable caller input; execution state is never stored here. */
record DefaultBusinessActionInput<P, D>(boolean hasParams, P params, boolean hasData, D data)
    implements BusinessActionInput<P, D> {
}
