# AdminAnalyticsControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**getAccountAnalytics**](AdminAnalyticsControllerApi.md#getaccountanalytics) | **GET** /api/v1/admin/analytics/accounts/{accountId} |  |
| [**getCallerTenantAnalytics**](AdminAnalyticsControllerApi.md#getcallertenantanalytics) | **GET** /api/v1/admin/analytics/tenant |  |
| [**getFleetAnalytics**](AdminAnalyticsControllerApi.md#getfleetanalytics) | **GET** /api/v1/admin/analytics/fleet |  |
| [**getNamespaceAnalytics**](AdminAnalyticsControllerApi.md#getnamespaceanalytics) | **GET** /api/v1/admin/analytics/namespaces/{namespaceId} |  |
| [**getTenantAnalytics**](AdminAnalyticsControllerApi.md#gettenantanalytics) | **GET** /api/v1/admin/analytics/tenants/{tenantId} |  |
| [**getTimeseries**](AdminAnalyticsControllerApi.md#gettimeseries) | **GET** /api/v1/admin/analytics/timeseries |  |



## getAccountAnalytics

> AccountAnalyticsRollup getAccountAnalytics(accountId)



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetAccountAnalyticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
  } satisfies GetAccountAnalyticsRequest;

  try {
    const data = await api.getAccountAnalytics(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **accountId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AccountAnalyticsRollup**](AccountAnalyticsRollup.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCallerTenantAnalytics

> TenantAnalyticsRollup getCallerTenantAnalytics(tenantId)



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetCallerTenantAnalyticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
  } satisfies GetCallerTenantAnalyticsRequest;

  try {
    const data = await api.getCallerTenantAnalytics(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **tenantId** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**TenantAnalyticsRollup**](TenantAnalyticsRollup.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getFleetAnalytics

> FleetAnalyticsRollup getFleetAnalytics()



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetFleetAnalyticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  try {
    const data = await api.getFleetAnalytics();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**FleetAnalyticsRollup**](FleetAnalyticsRollup.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getNamespaceAnalytics

> NamespaceAnalyticsRollup getNamespaceAnalytics(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetNamespaceAnalyticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies GetNamespaceAnalyticsRequest;

  try {
    const data = await api.getNamespaceAnalytics(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **namespaceId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**NamespaceAnalyticsRollup**](NamespaceAnalyticsRollup.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getTenantAnalytics

> TenantAnalyticsRollup getTenantAnalytics(tenantId)



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetTenantAnalyticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies GetTenantAnalyticsRequest;

  try {
    const data = await api.getTenantAnalytics(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **tenantId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**TenantAnalyticsRollup**](TenantAnalyticsRollup.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getTimeseries

> AnalyticsTimeseriesResponse getTimeseries(scope, tenantId, accountId, namespaceId, from, to, limit)



### Example

```ts
import {
  Configuration,
  AdminAnalyticsControllerApi,
} from '';
import type { GetTimeseriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAnalyticsControllerApi(config);

  const body = {
    // 'FLEET' | 'TENANT' | 'ACCOUNT' | 'NAMESPACE' (optional)
    scope: scope_example,
    // string (optional)
    tenantId: tenantId_example,
    // string (optional)
    accountId: accountId_example,
    // string (optional)
    namespaceId: namespaceId_example,
    // Date (optional)
    from: 2013-10-20T19:20:30+01:00,
    // Date (optional)
    to: 2013-10-20T19:20:30+01:00,
    // number (optional)
    limit: 56,
  } satisfies GetTimeseriesRequest;

  try {
    const data = await api.getTimeseries(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **scope** | `FLEET`, `TENANT`, `ACCOUNT`, `NAMESPACE` |  | [Optional] [Defaults to `undefined`] [Enum: FLEET, TENANT, ACCOUNT, NAMESPACE] |
| **tenantId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **accountId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **namespaceId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **from** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **to** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **limit** | `number` |  | [Optional] [Defaults to `100`] |

### Return type

[**AnalyticsTimeseriesResponse**](AnalyticsTimeseriesResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

