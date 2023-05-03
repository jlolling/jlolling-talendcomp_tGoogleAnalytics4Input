/**
 * Copyright 2015 Jan Lolling jan.lolling@gmail.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jlo.talendcomp.google.analytics.ga4.v1;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.analytics.data.v1beta.DateRange;
import com.google.analytics.data.v1beta.Dimension;
import com.google.analytics.data.v1beta.FilterExpression;
import com.google.analytics.data.v1beta.FilterExpressionList;
import com.google.analytics.data.v1beta.Metric;
import com.google.analytics.data.v1beta.OrderBy;
import com.google.analytics.data.v1beta.OrderBy.DimensionOrderBy;
import com.google.analytics.data.v1beta.RunReportRequest;
import com.google.analytics.data.v1beta.RunReportResponse;
import com.google.analytics.data.v1beta.Row;
import com.google.api.gax.rpc.ApiException;

import de.jlo.talendcomp.google.analytics.ga4.DimensionValue;
import de.jlo.talendcomp.google.analytics.ga4.GoogleAnalyticsBase;
import de.jlo.talendcomp.google.analytics.ga4.MetricValue;
import de.jlo.talendcomp.google.analytics.ga4.Util;

public class GoogleAnalyticsInput extends GoogleAnalyticsBase {

	private static final Map<String, GoogleAnalyticsInput> clientCache = new HashMap<String, GoogleAnalyticsInput>();
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	private String startDate = null;
	private String endDate = null;
	private String metrics = null;
	private String dimensions = null;
	private String sortsByDimension = null;
	private String dimensionFilters = null;
	private String metricFilters = null;
	private String propertyId;
	private int fetchSize = 10000;
	private int lastFetchedRowCount = 0;
	private int expectedTotalPlainRowCount = 0;
	private int currentOverallPlainRowCount = 0;
	private int currentPlainRowIndex = 0;
	private int startIndex = 1;
	private List<DimensionValue> currentResultRowDimensionValues;
	private List<MetricValue> currentResultRowMetricValues;
	private Date currentDate;
	private static final String DATE_DIM = "date";
	private boolean excludeDate = false; 
	private List<Dimension> listDimensions = null;
	private List<Metric> listMetrics = null;
	private RunReportRequest.Builder reportRequestBuilder = null;
	private RunReportRequest reportRequest;
	private RunReportResponse response;
	private List<Row> lastResultSet = null;
	private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
	private int errorCode = 0;
	private boolean success = true;
	private boolean keepEmptyRows = true;

	public static void putIntoCache(String key, GoogleAnalyticsInput gai) {
		clientCache.put(key, gai);
	}
	
	public static GoogleAnalyticsInput getFromCache(String key) {
		return clientCache.get(key);
	}
	
	/**
	 * set the maximum rows per fetch
	 * 
	 * @param fetchSize
	 */
	public void setFetchSize(Integer fetchSize) {
		if (fetchSize != null && fetchSize > 0) {
			this.fetchSize = fetchSize;
		}
	}

	public void setPropertyId(String propertyId) {
		if (propertyId == null || propertyId.trim().isEmpty()) {
			throw new IllegalArgumentException("PropertyId cannot be null or empty");
		}
		this.propertyId = propertyId;
	}

	public void setPropertyId(Integer propertyId) {
		if (propertyId == null || propertyId < 1) {
			throw new IllegalArgumentException("PropertyId cannot be 0 or empty");
		}
		this.propertyId = String.valueOf(propertyId);
	}

	public void setPropertyId(Long propertyId) {
		if (propertyId == null || propertyId < 1) {
			throw new IllegalArgumentException("PropertyId cannot be 0 or empty");
		}
		this.propertyId = String.valueOf(propertyId);
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * Format: yyyy-MM-dd
	 * @param yyyyMMdd
	 */
	public void setStartDate(String yyyyMMdd) {
		this.startDate = yyyyMMdd;
	}

	public void setStartDate(Date startDate) {
		this.startDate = sdf.format(startDate);
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * Format: yyyy-MM-dd
	 * @param yyyyMMdd
	 */
	public void setEndDate(String yyyyMMdd) {
		this.endDate = yyyyMMdd;
	}

	/**
	 * for selecting data for one day: set start date == end date
	 * 
	 * @param yyyyMMdd
	 */
	public void setEndDate(Date endDate) {
		this.endDate = sdf.format(endDate);
	}

	public void setMetrics(String metrics) {
		if (metrics == null || metrics.trim().isEmpty()) {
			throw new IllegalArgumentException("Metrics cannot be null or empty");
		}
		this.metrics = metrics.trim();
	}

	public void setDimensions(String dimensions) {
		if (dimensions != null && dimensions.trim().isEmpty() == false) {
			this.dimensions = dimensions.trim();
		} else {
			this.dimensions = null;
		}
	}

	public void setSorts(String sorts) {
		if (sorts != null && sorts.trim().isEmpty() == false) {
			this.sortsByDimension = sorts;
		} else {
			this.sortsByDimension = null;
		}
	}

	/**
	 * use operators like:
	 * == exact match
	 * =@ contains
	 * =~ matches regular expression
	 * != does not contains
	 * separate filters with , for OR and ; for AND
	 * @param filters
	 */
	public void setDimensionFilters(String filters) {
		if (filters != null && filters.trim().isEmpty() == false) {
			this.dimensionFilters = filters.trim();
		} else {
			this.dimensionFilters = null;
		}
	}
	
	/**
	 * use operators like:
	 * == exact match
	 * =@ contains
	 * =~ matches regular expression
	 * != does not contains
	 * separate filters with , for OR and ; for AND
	 * @param filters
	 */
	public void setMetricFilters(String filters) {
		if (filters != null && filters.trim().isEmpty() == false) {
			this.metricFilters = filters.trim();
		} else {
			this.metricFilters = null;
		}
	}

	private void prepareInitialRequestParameters() throws Exception {
		if (propertyId == null || propertyId.length() < 5) {
			throw new Exception("propertyId is missing or not long enough");
		}
		if (startDate == null || startDate.trim().isEmpty()) {
			throw new Exception("Missing start date!");
		}
		if (endDate == null || endDate.trim().isEmpty()) {
			throw new Exception("Missing end date!");
		}
		reportRequestBuilder = RunReportRequest.newBuilder();
		reportRequestBuilder.setProperty("properties/" + propertyId);
		reportRequestBuilder.setKeepEmptyRows(keepEmptyRows);
		reportRequestBuilder.setOffset(0L);
		reportRequestBuilder.setLimit(fetchSize);
		setupDimensions();
		setupMetrics();
		setupDimensionFilters();
		setupMetricsFilters();
		setupOrderBys();
		reportRequestBuilder.addDateRanges(DateRange.newBuilder()
				.setStartDate(startDate)
				.setEndDate(endDate));
		currentDate = sdf.parse(endDate);
	}
	
	public void executeQuery() throws Exception {
		prepareInitialRequestParameters();
		doExecute();
		currentOverallPlainRowCount = 0;
		startIndex = 0;
		maxCountNormalizedValues = 0;
		currentNormalizedValueIndex = 0;
	}
			
	private int maxRetriesInCaseOfErrors = 5;
	private int currentAttempt = 0;
	private String errorMessage = null;
	
	private void doExecute() throws Exception {
		reportRequest = reportRequestBuilder.build();
		response = null;
		int waitTime = 1000;
		for (currentAttempt = 0; currentAttempt < maxRetriesInCaseOfErrors; currentAttempt++) {
			errorCode = 0;
			try {
				response = getAnalyticsClient().runReport(reportRequest);
				success = true;
				break;
			} catch (ApiException apie) {
				success = false;
				warn("Got error:" + apie.getMessage());
				errorMessage = apie.getMessage();
				errorCode = apie.getStatusCode().getCode().getHttpStatusCode();
				if (apie.isRetryable() == false) {
					throw new Exception("Request is not retryable and failed: " + apie.getMessage(), apie);
				}
				if (currentAttempt == (maxRetriesInCaseOfErrors - 1)) {
					error("All retries (#" + (currentAttempt+1) + " of " + maxRetriesInCaseOfErrors + ") request failed:" + apie.getMessage(), apie);
					throw apie;
				} else {
					// wait
					try {
						info("Retry #" + (currentAttempt+1) + " request in " + waitTime + "ms");
						Thread.sleep(waitTime);
					} catch (InterruptedException ie) {}
					int random = (int) Math.random() * 500;
					waitTime = (waitTime * 2) + random;
				}
			}
		}
		if (response == null) {
			throw new Exception("No response received!");
		}
		expectedTotalPlainRowCount = response.getRowCount();
		lastResultSet = response.getRowsList();
		if (lastResultSet == null) {
			// fake an empty result set to avoid breaking further processing
			lastResultSet = new ArrayList<Row>();
		}
		lastFetchedRowCount = lastResultSet.size();
		currentPlainRowIndex = 0;
	}
	
	/**
	 * checks if more result set available
	 * @return true if more data sets available
	 * @throws Exception if the necessary next request fails 
	 */
	public boolean hasNextPlainRecord() throws Exception {
		if (response == null) {
			throw new IllegalStateException("No query executed before");
		}
		// the initial call doExecute will be done before.
		// check if a new call is necessary
		if (currentPlainRowIndex == lastFetchedRowCount // we are at the end of the current fetched records
				&& lastFetchedRowCount > 0 // there are fetched rows last time
				&& currentOverallPlainRowCount < expectedTotalPlainRowCount) { // and we have not reached the final end
			// prepare the request for the next records
			startIndex = startIndex + lastFetchedRowCount;
			reportRequestBuilder.setOffset(startIndex);
			// run first or next request
			doExecute();
		}
		if (lastFetchedRowCount > 0 && currentPlainRowIndex < lastFetchedRowCount) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * called in jet code
	 * @return
	 */
	public List<String> getNextPlainRecord() {
		if (response == null) {
			throw new IllegalStateException("No query executed before");
		}
		currentOverallPlainRowCount++;
		Row row = lastResultSet.get(currentPlainRowIndex++); 
		return buildRecord(row);
	}

	private List<String> buildRecord(Row row) {
		List<String> record = new ArrayList<String>();
		List<com.google.analytics.data.v1beta.DimensionValue> dimValues = row.getDimensionValuesList();
		for (com.google.analytics.data.v1beta.DimensionValue v : dimValues) {
			record.add(v.getValue());
		}
		List<com.google.analytics.data.v1beta.MetricValue> metricValues = row.getMetricValuesList();
		for (com.google.analytics.data.v1beta.MetricValue v : metricValues) {
			record.add(v.getValue());
		}
		return record;
	}
	
	/**
	 * called in jet code
	 * @return
	 */
	public boolean nextNormalizedRecord() throws Exception {
		if (maxCountNormalizedValues == 0) {
			// at start we do not have any records
			if (hasNextPlainRecord()) {
				buildNormalizedRecords(getNextPlainRecord());
			}
		}
		if (maxCountNormalizedValues > 0) {
			if (currentNormalizedValueIndex < maxCountNormalizedValues) {
				currentNormalizedValueIndex++;
				return true;
			} else if (currentNormalizedValueIndex == maxCountNormalizedValues) {
				// the end of the normalized rows reached, fetch the next data row
				if (hasNextPlainRecord()) {
					if (buildNormalizedRecords(getNextPlainRecord())) {
						currentNormalizedValueIndex++;
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public DimensionValue getCurrentDimensionValue() {
		if (currentNormalizedValueIndex == 0) {
			throw new IllegalStateException("Call nextNormalizedRecord() at first!");
		}
		if (currentNormalizedValueIndex <= currentResultRowDimensionValues.size()) {
			return currentResultRowDimensionValues.get(currentNormalizedValueIndex - 1);
		} else {
			return null;
		}
	}
	
	public MetricValue getCurrentMetricValue() {
		if (currentNormalizedValueIndex == 0) {
			throw new IllegalStateException("Call nextNormalizedRecord() at first!");
		}
		if (currentNormalizedValueIndex <= currentResultRowMetricValues.size()) {
			return currentResultRowMetricValues.get(currentNormalizedValueIndex - 1);
		} else {
			return null;
		}
	}

	private int maxCountNormalizedValues = 0;
	private int currentNormalizedValueIndex = 0;
	
	private void setMaxCountNormalizedValues(int count) {
		if (count > maxCountNormalizedValues) {
			maxCountNormalizedValues = count;
		}
	}
	
	private boolean buildNormalizedRecords(List<String> oneRow) {
		maxCountNormalizedValues = 0;
		currentNormalizedValueIndex = 0;
		buildDimensionValues(oneRow);
		buildMetricValues(oneRow);
		return maxCountNormalizedValues > 0;
	}
	
	private List<DimensionValue> buildDimensionValues(List<String> oneRow) {
		int index = 0;
		final List<DimensionValue> oneRowDimensionValues = new ArrayList<DimensionValue>();
		for (; index < listDimensions.size(); index++) {
			String dimName = listDimensions.get(index).getName();
			DimensionValue dm = new DimensionValue();
			dm.name = dimName.trim();
			dm.value = oneRow.get(index);
			dm.rowNum = currentOverallPlainRowCount;
        	if (excludeDate && DATE_DIM.equals(dm.name.trim().toLowerCase())) {
        		try {
        			if (dm.value != null) {
    					currentDate = dateFormatter.parse(dm.value);
        			} else {
        				currentDate = null;
        			}
				} catch (ParseException e) {
					currentDate = null;
				}
        	} else {
    			oneRowDimensionValues.add(dm);
        	}
		}
		currentResultRowDimensionValues = oneRowDimensionValues;
		setMaxCountNormalizedValues(currentResultRowDimensionValues.size());
		return oneRowDimensionValues;
	}

	private List<MetricValue> buildMetricValues(List<String> oneRow) {
		int index = 0;
		final List<MetricValue> oneRowMetricValues = new ArrayList<MetricValue>();
		for (; index < listMetrics.size(); index++) {
			MetricValue mv = new MetricValue();
			Metric metric = listMetrics.get(index);
			mv.name = metric.getName();
			if (mv.name == null) {
				mv.name = metric.getExpression();
			}
			mv.rowNum = currentOverallPlainRowCount;
			int countDimensions = listDimensions.size();
			String valueStr = oneRow.get(index + countDimensions);
			try {
				mv.value = Util.convertToDouble(valueStr, Locale.ENGLISH.toString());
				oneRowMetricValues.add(mv);
			} catch (Exception e) {
				throw new IllegalStateException("Failed to build a double value for the metric:" + mv.name + " and value String:" + valueStr);
			}
		}
		currentResultRowMetricValues = oneRowMetricValues;
		setMaxCountNormalizedValues(currentResultRowMetricValues.size());
		return oneRowMetricValues;
	}

	public List<String> getTotalsDataset() {
		if (response == null) {
			throw new IllegalStateException("No query executed before");
		}
		List<Row> totals = response.getTotalsList();
		Row oneTotal = null;
		if (totals != null && totals.size() > 0) {
			oneTotal = totals.get(0);
		}
		List<String> totalResult = new ArrayList<String>();
		int countDimensions = listDimensions.size();
		int countMetrics = listMetrics.size();
		int metricIndex = 0;
		for (int i = 0; i < countDimensions + countMetrics; i++) {
			if (i < countDimensions) {
				totalResult.add("total");
			} else {
				totalResult.add(oneTotal
						.getMetricValues(metricIndex++)
						.getValue());
			}
		}
		return totalResult;
	}

	public int getCurrentIndexOverAll() {
		return startIndex + currentPlainRowIndex;
	}

	public int getOverAllCountRows() {
		return currentOverallPlainRowCount;
	}

	@Override
	public Date getCurrentDate() throws ParseException {
		return currentDate;
	}

	public void setExcludeDate(boolean excludeDate) {
		this.excludeDate = excludeDate;
	}

	@Override
	public int getErrorCode() {
		return errorCode;
	}

	@Override
	public boolean isSuccess() {
		return success;
	}
	
	public void setMaxRetriesInCaseOfErrors(Integer maxRetriesInCaseOfErrors) {
		if (maxRetriesInCaseOfErrors != null) {
			this.maxRetriesInCaseOfErrors = maxRetriesInCaseOfErrors;
		}
	}

	public String getErrorMessage() {
		return errorMessage;
	}
	
	private void setupDimensions() {
		reportRequestBuilder.clearDimensions();
		listDimensions = new ArrayList<Dimension>();
		if (dimensions != null) {
			String[] dimArray = dimensions.split(",");
			int index = 0;
			for (String dimName : dimArray) {
				Dimension dimension = Dimension.newBuilder().setName(dimName).build();
				listDimensions.add(dimension);
				reportRequestBuilder.addDimensions(dimension);
				debug("add dimension: " + dimension.toString() + " at " + index);
				index++;
			}
		}
	}
	

	private void setupDimensionFilters() throws Exception {
		reportRequestBuilder.clearDimensionFilter();
		if (dimensionFilters != null) {
			String[] filters = dimensionFilters.split(",");
			if (filters.length > 1) {
				for (String f : filters) {
					reportRequestBuilder.setDimensionFilter(FilterExpression.newBuilder()
							.setAndGroup(FilterExpressionList.newBuilder()
									.addExpressions(FilterExpressionBuilder.buildStringFilterExpression(f))));
				}
			} else if (filters.length == 1) {
				reportRequestBuilder.setDimensionFilter(FilterExpressionBuilder.buildStringFilterExpression(filters[0]));
			}
		}
	}
	

	private void setupMetricsFilters() throws Exception {
		reportRequestBuilder.clearMetricFilter();
		if (metricFilters != null) {
			String[] filters = metricFilters.split(",");
			if (filters.length > 1) {
				for (String f : filters) {
					reportRequestBuilder.setMetricFilter(FilterExpression.newBuilder()
							.setAndGroup(FilterExpressionList.newBuilder()
									.addExpressions(FilterExpressionBuilder.buildNumericFilterExpression(f))));
				}
			} else if (filters.length == 1) {
				reportRequestBuilder.setMetricFilter(FilterExpressionBuilder.buildNumericFilterExpression(filters[0]));
			}
		}
	}

	private Metric buildMetric(String metricStr) {
		if (metricStr != null && metricStr.trim().isEmpty() == false) {
			metricStr = metricStr.trim();
			Metric.Builder metricBuilder = Metric.newBuilder();
			int eqPos = metricStr.indexOf("=");
			if (eqPos != -1) {
				String aliasAndType = metricStr.substring(0, eqPos).trim();
				if (aliasAndType != null && aliasAndType.isEmpty() == false) {
					metricBuilder.setName(aliasAndType);
				} else {
					throw new IllegalStateException("Invalid calculated metric (without name) found: '" + metricStr + "'");
				}
				if (eqPos < metricStr.length() - 2) {
					String expression = metricStr.substring(eqPos + 1).trim();
					metricBuilder.setExpression(expression);
				} else {
					throw new IllegalStateException("Invalid calculated metric (without expression) found: '" + metricStr + "'");
				}
			} else {
				metricBuilder.setName(metricStr);
			}
			return metricBuilder.build();
		} else {
			return null;
		}
	}

	private void setupMetrics() {
		if (metrics == null) {
			throw new IllegalStateException("Metrics are not set!");
		}
		reportRequestBuilder.clearMetrics();
		listMetrics = new ArrayList<Metric>();
		String[] metricArray = metrics.split(",");
		// a metric can consists of an alias and the expression separated by =
		int index = 0;
		for (String metricStr : metricArray) {
			Metric metric = buildMetric(metricStr);
			if (metric != null) {
				listMetrics.add(metric);
				reportRequestBuilder.addMetrics(metric);
				debug("add metric: " + metric.toString() + " at " + index);
				index++;
			}
		}
		if (listMetrics.isEmpty()) {
			throw new IllegalStateException("No metrics found in metric string: " + metrics);
		}
	}
	
	private void setupOrderBys() {
		reportRequestBuilder.clearOrderBys();
		if (sortsByDimension != null) {
			String[] sortArray = sortsByDimension.split(",");
			for (String sort : sortArray) {
				if (sort != null && sort.trim().isEmpty() == false) {
					sort = sort.trim();
					OrderBy.Builder orderbyBuilder = OrderBy.newBuilder();
					if (sort.startsWith("-")) {
						orderbyBuilder.setDesc(true);
						orderbyBuilder.setDimension(DimensionOrderBy.newBuilder().setDimensionName(sort.substring(1)));
					} else {
						orderbyBuilder.setDesc(false);
						orderbyBuilder.setDimension(DimensionOrderBy.newBuilder().setDimensionName(sort));
					}
					reportRequestBuilder.addOrderBys(orderbyBuilder);
				}
			}
		}
	}

	public int getExpectedTotalPlainRowCount() {
		return expectedTotalPlainRowCount;
	}

	public int getFetchSize() {
		return fetchSize;
	}

}
