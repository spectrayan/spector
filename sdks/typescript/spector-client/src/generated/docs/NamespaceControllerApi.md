# NamespaceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createGrant**](NamespaceControllerApi.md#creategrantoperation) | **POST** /api/v1/namespaces/{slugOrId}/grants |  |
| [**createNamespace**](NamespaceControllerApi.md#createnamespaceoperation) | **POST** /api/v1/namespaces |  |
| [**deleteNamespace**](NamespaceControllerApi.md#deletenamespace) | **DELETE** /api/v1/namespaces/{slugOrId} |  |
| [**getNamespace**](NamespaceControllerApi.md#getnamespace) | **GET** /api/v1/namespaces/{slugOrId} |  |
| [**listGrants**](NamespaceControllerApi.md#listgrants) | **GET** /api/v1/namespaces/{slugOrId}/grants |  |
| [**listNamespaces**](NamespaceControllerApi.md#listnamespaces) | **GET** /api/v1/namespaces |  |
| [**resetNamespace**](NamespaceControllerApi.md#resetnamespace) | **POST** /api/v1/namespaces/{slugOrId}/reset |  |
| [**revokeGrant**](NamespaceControllerApi.md#revokegrant) | **DELETE** /api/v1/namespaces/{slugOrId}/grants/{grantId} |  |
| [**setLegalHold**](NamespaceControllerApi.md#setlegalhold) | **POST** /api/v1/namespaces/{slugOrId}/legal-hold |  |
| [**updateNamespace**](NamespaceControllerApi.md#updatenamespaceoperation) | **PUT** /api/v1/namespaces/{slugOrId} |  |



## createGrant

> GrantResponse createGrant(slugOrId, createGrantRequest)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { CreateGrantOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
    // CreateGrantRequest
    createGrantRequest: ...,
  } satisfies CreateGrantOperationRequest;

  try {
    const data = await api.createGrant(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |
| **createGrantRequest** | [CreateGrantRequest](CreateGrantRequest.md) |  | |

### Return type

[**GrantResponse**](GrantResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## createNamespace

> NamespaceResponse createNamespace(createNamespaceRequest)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { CreateNamespaceOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // CreateNamespaceRequest
    createNamespaceRequest: ...,
  } satisfies CreateNamespaceOperationRequest;

  try {
    const data = await api.createNamespace(body);
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
| **createNamespaceRequest** | [CreateNamespaceRequest](CreateNamespaceRequest.md) |  | |

### Return type

[**NamespaceResponse**](NamespaceResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## deleteNamespace

> deleteNamespace(slugOrId)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { DeleteNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
  } satisfies DeleteNamespaceRequest;

  try {
    const data = await api.deleteNamespace(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getNamespace

> NamespaceResponse getNamespace(slugOrId)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { GetNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
  } satisfies GetNamespaceRequest;

  try {
    const data = await api.getNamespace(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**NamespaceResponse**](NamespaceResponse.md)

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


## listGrants

> Array&lt;GrantResponse&gt; listGrants(slugOrId)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { ListGrantsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
  } satisfies ListGrantsRequest;

  try {
    const data = await api.listGrants(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**Array&lt;GrantResponse&gt;**](GrantResponse.md)

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


## listNamespaces

> Array&lt;NamespaceResponse&gt; listNamespaces()



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { ListNamespacesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  try {
    const data = await api.listNamespaces();
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

[**Array&lt;NamespaceResponse&gt;**](NamespaceResponse.md)

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


## resetNamespace

> { [key: string]: string; } resetNamespace(slugOrId)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { ResetNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
  } satisfies ResetNamespaceRequest;

  try {
    const data = await api.resetNamespace(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |

### Return type

**{ [key: string]: string; }**

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


## revokeGrant

> revokeGrant(slugOrId, grantId)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { RevokeGrantRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
    // string
    grantId: grantId_example,
  } satisfies RevokeGrantRequest;

  try {
    const data = await api.revokeGrant(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |
| **grantId** | `string` |  | [Defaults to `undefined`] |

### Return type

`void` (Empty response body)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## setLegalHold

> NamespaceResponse setLegalHold(slugOrId, legalHoldRequest)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { SetLegalHoldRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
    // LegalHoldRequest
    legalHoldRequest: ...,
  } satisfies SetLegalHoldRequest;

  try {
    const data = await api.setLegalHold(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |
| **legalHoldRequest** | [LegalHoldRequest](LegalHoldRequest.md) |  | |

### Return type

[**NamespaceResponse**](NamespaceResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## updateNamespace

> NamespaceResponse updateNamespace(slugOrId, updateNamespaceRequest)



### Example

```ts
import {
  Configuration,
  NamespaceControllerApi,
} from '';
import type { UpdateNamespaceOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new NamespaceControllerApi(config);

  const body = {
    // string
    slugOrId: slugOrId_example,
    // UpdateNamespaceRequest
    updateNamespaceRequest: ...,
  } satisfies UpdateNamespaceOperationRequest;

  try {
    const data = await api.updateNamespace(body);
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
| **slugOrId** | `string` |  | [Defaults to `undefined`] |
| **updateNamespaceRequest** | [UpdateNamespaceRequest](UpdateNamespaceRequest.md) |  | |

### Return type

[**NamespaceResponse**](NamespaceResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

