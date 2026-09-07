# CacheControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**clearAllCaches**](CacheControllerApi.md#clearallcaches) | **DELETE** /api/v1/admin/cache |  |
| [**clearAllCaches1**](CacheControllerApi.md#clearallcaches1) | **DELETE** /api/v1/cache |  |
| [**clearCache**](CacheControllerApi.md#clearcache) | **DELETE** /api/v1/admin/cache/{cacheName} |  |
| [**clearCache1**](CacheControllerApi.md#clearcache1) | **DELETE** /api/v1/cache/{cacheName} |  |
| [**evictKey**](CacheControllerApi.md#evictkey) | **DELETE** /api/v1/admin/cache/{cacheName}/{key} |  |
| [**evictKey1**](CacheControllerApi.md#evictkey1) | **DELETE** /api/v1/cache/{cacheName}/{key} |  |
| [**getCacheDetails**](CacheControllerApi.md#getcachedetails) | **GET** /api/v1/admin/cache/{cacheName} |  |
| [**getCacheDetails1**](CacheControllerApi.md#getcachedetails1) | **GET** /api/v1/cache/{cacheName} |  |
| [**getCacheEntry**](CacheControllerApi.md#getcacheentry) | **GET** /api/v1/admin/cache/{cacheName}/{key} |  |
| [**getCacheEntry1**](CacheControllerApi.md#getcacheentry1) | **GET** /api/v1/cache/{cacheName}/{key} |  |
| [**listCaches**](CacheControllerApi.md#listcaches) | **GET** /api/v1/admin/cache |  |
| [**listCaches1**](CacheControllerApi.md#listcaches1) | **GET** /api/v1/cache |  |



## clearAllCaches

> { [key: string]: any | null; } clearAllCaches()



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ClearAllCachesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  try {
    const data = await api.clearAllCaches();
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

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## clearAllCaches1

> { [key: string]: any | null; } clearAllCaches1()



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ClearAllCaches1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  try {
    const data = await api.clearAllCaches1();
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

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## clearCache

> { [key: string]: any | null; } clearCache(cacheName)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ClearCacheRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
  } satisfies ClearCacheRequest;

  try {
    const data = await api.clearCache(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## clearCache1

> { [key: string]: any | null; } clearCache1(cacheName)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ClearCache1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
  } satisfies ClearCache1Request;

  try {
    const data = await api.clearCache1(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## evictKey

> { [key: string]: any | null; } evictKey(cacheName, key)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { EvictKeyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
    // string
    key: key_example,
  } satisfies EvictKeyRequest;

  try {
    const data = await api.evictKey(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |
| **key** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## evictKey1

> { [key: string]: any | null; } evictKey1(cacheName, key)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { EvictKey1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
    // string
    key: key_example,
  } satisfies EvictKey1Request;

  try {
    const data = await api.evictKey1(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |
| **key** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCacheDetails

> CacheDetailDto getCacheDetails(cacheName)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { GetCacheDetailsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
  } satisfies GetCacheDetailsRequest;

  try {
    const data = await api.getCacheDetails(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CacheDetailDto**](CacheDetailDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCacheDetails1

> CacheDetailDto getCacheDetails1(cacheName)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { GetCacheDetails1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
  } satisfies GetCacheDetails1Request;

  try {
    const data = await api.getCacheDetails1(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |

### Return type

[**CacheDetailDto**](CacheDetailDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCacheEntry

> { [key: string]: any | null; } getCacheEntry(cacheName, key)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { GetCacheEntryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
    // string
    key: key_example,
  } satisfies GetCacheEntryRequest;

  try {
    const data = await api.getCacheEntry(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |
| **key** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getCacheEntry1

> { [key: string]: any | null; } getCacheEntry1(cacheName, key)



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { GetCacheEntry1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  const body = {
    // string
    cacheName: cacheName_example,
    // string
    key: key_example,
  } satisfies GetCacheEntry1Request;

  try {
    const data = await api.getCacheEntry1(body);
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
| **cacheName** | `string` |  | [Defaults to `undefined`] |
| **key** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: any | null; }**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCaches

> Array&lt;CacheSummaryDto&gt; listCaches()



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ListCachesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  try {
    const data = await api.listCaches();
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

[**Array&lt;CacheSummaryDto&gt;**](CacheSummaryDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## listCaches1

> Array&lt;CacheSummaryDto&gt; listCaches1()



### Example

```ts
import {
  Configuration,
  CacheControllerApi,
} from '';
import type { ListCaches1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new CacheControllerApi(config);

  try {
    const data = await api.listCaches1();
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

[**Array&lt;CacheSummaryDto&gt;**](CacheSummaryDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

