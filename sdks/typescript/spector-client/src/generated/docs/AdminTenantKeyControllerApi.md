# AdminTenantKeyControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**configureTenantKey**](AdminTenantKeyControllerApi.md#configuretenantkeyoperation) | **PUT** /api/v1/admin/tenants/{tenantId}/keys |  |
| [**deleteTenantKey**](AdminTenantKeyControllerApi.md#deletetenantkey) | **DELETE** /api/v1/admin/tenants/{tenantId}/keys |  |
| [**getTenantKeyStatus**](AdminTenantKeyControllerApi.md#gettenantkeystatus) | **GET** /api/v1/admin/tenants/{tenantId}/keys |  |
| [**revokeTenantKey**](AdminTenantKeyControllerApi.md#revoketenantkeyoperation) | **POST** /api/v1/admin/tenants/{tenantId}/keys/revoke |  |
| [**rotateNamespaceDek**](AdminTenantKeyControllerApi.md#rotatenamespacedekoperation) | **POST** /api/v1/admin/tenants/{tenantId}/keys/rotate-dek |  |
| [**rotateTenantKek**](AdminTenantKeyControllerApi.md#rotatetenantkekoperation) | **POST** /api/v1/admin/tenants/{tenantId}/keys/rotate-kek |  |
| [**validateTenantKey**](AdminTenantKeyControllerApi.md#validatetenantkey) | **POST** /api/v1/admin/tenants/{tenantId}/keys/validate |  |



## configureTenantKey

> TenantKeyStatusResponse configureTenantKey(tenantId, configureTenantKeyRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { ConfigureTenantKeyOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
    // ConfigureTenantKeyRequest
    configureTenantKeyRequest: ...,
  } satisfies ConfigureTenantKeyOperationRequest;

  try {
    const data = await api.configureTenantKey(body);
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
| **configureTenantKeyRequest** | [ConfigureTenantKeyRequest](ConfigureTenantKeyRequest.md) |  | |

### Return type

[**TenantKeyStatusResponse**](TenantKeyStatusResponse.md)

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


## deleteTenantKey

> TenantKeyStatusResponse deleteTenantKey(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { DeleteTenantKeyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies DeleteTenantKeyRequest;

  try {
    const data = await api.deleteTenantKey(body);
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

[**TenantKeyStatusResponse**](TenantKeyStatusResponse.md)

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


## getTenantKeyStatus

> TenantKeyStatusResponse getTenantKeyStatus(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { GetTenantKeyStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies GetTenantKeyStatusRequest;

  try {
    const data = await api.getTenantKeyStatus(body);
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

[**TenantKeyStatusResponse**](TenantKeyStatusResponse.md)

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


## revokeTenantKey

> TenantKeyRevocationResponse revokeTenantKey(tenantId, revokeTenantKeyRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { RevokeTenantKeyOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
    // RevokeTenantKeyRequest (optional)
    revokeTenantKeyRequest: ...,
  } satisfies RevokeTenantKeyOperationRequest;

  try {
    const data = await api.revokeTenantKey(body);
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
| **revokeTenantKeyRequest** | [RevokeTenantKeyRequest](RevokeTenantKeyRequest.md) |  | [Optional] |

### Return type

[**TenantKeyRevocationResponse**](TenantKeyRevocationResponse.md)

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


## rotateNamespaceDek

> DekRotationResponse rotateNamespaceDek(tenantId, rotateNamespaceDekRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { RotateNamespaceDekOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
    // RotateNamespaceDekRequest (optional)
    rotateNamespaceDekRequest: ...,
  } satisfies RotateNamespaceDekOperationRequest;

  try {
    const data = await api.rotateNamespaceDek(body);
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
| **rotateNamespaceDekRequest** | [RotateNamespaceDekRequest](RotateNamespaceDekRequest.md) |  | [Optional] |

### Return type

[**DekRotationResponse**](DekRotationResponse.md)

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


## rotateTenantKek

> KekRotationResponse rotateTenantKek(tenantId, rotateTenantKekRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { RotateTenantKekOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
    // RotateTenantKekRequest (optional)
    rotateTenantKekRequest: ...,
  } satisfies RotateTenantKekOperationRequest;

  try {
    const data = await api.rotateTenantKek(body);
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
| **rotateTenantKekRequest** | [RotateTenantKekRequest](RotateTenantKekRequest.md) |  | [Optional] |

### Return type

[**KekRotationResponse**](KekRotationResponse.md)

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


## validateTenantKey

> TenantKeyValidationResponse validateTenantKey(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantKeyControllerApi,
} from '';
import type { ValidateTenantKeyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantKeyControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies ValidateTenantKeyRequest;

  try {
    const data = await api.validateTenantKey(body);
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

[**TenantKeyValidationResponse**](TenantKeyValidationResponse.md)

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

