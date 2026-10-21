# AdminTenantControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createTenant**](AdminTenantControllerApi.md#createtenantoperation) | **POST** /api/v1/admin/tenants |  |
| [**deleteTenant**](AdminTenantControllerApi.md#deletetenant) | **DELETE** /api/v1/admin/tenants/{tenantId} |  |
| [**getTenant**](AdminTenantControllerApi.md#gettenant) | **GET** /api/v1/admin/tenants/{tenantId} |  |
| [**listTenantAuditLogs**](AdminTenantControllerApi.md#listtenantauditlogs) | **GET** /api/v1/admin/tenants/{tenantId}/audit |  |
| [**listTenants**](AdminTenantControllerApi.md#listtenants) | **GET** /api/v1/admin/tenants |  |
| [**updateTenant**](AdminTenantControllerApi.md#updatetenantoperation) | **PUT** /api/v1/admin/tenants/{tenantId} |  |



## createTenant

> TenantResponse createTenant(createTenantRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { CreateTenantOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // CreateTenantRequest
    createTenantRequest: ...,
  } satisfies CreateTenantOperationRequest;

  try {
    const data = await api.createTenant(body);
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
| **createTenantRequest** | [CreateTenantRequest](CreateTenantRequest.md) |  | |

### Return type

[**TenantResponse**](TenantResponse.md)

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


## deleteTenant

> TenantResponse deleteTenant(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { DeleteTenantRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies DeleteTenantRequest;

  try {
    const data = await api.deleteTenant(body);
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

[**TenantResponse**](TenantResponse.md)

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


## getTenant

> TenantResponse getTenant(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { GetTenantRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies GetTenantRequest;

  try {
    const data = await api.getTenant(body);
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

[**TenantResponse**](TenantResponse.md)

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


## listTenantAuditLogs

> Array&lt;TenantAuditResponse&gt; listTenantAuditLogs(tenantId)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { ListTenantAuditLogsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
  } satisfies ListTenantAuditLogsRequest;

  try {
    const data = await api.listTenantAuditLogs(body);
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

[**Array&lt;TenantAuditResponse&gt;**](TenantAuditResponse.md)

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


## listTenants

> Array&lt;TenantResponse&gt; listTenants(status, includeDeleted)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { ListTenantsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // 'ACTIVE' | 'SUSPENDED' | 'DELETED' (optional)
    status: status_example,
    // boolean (optional)
    includeDeleted: true,
  } satisfies ListTenantsRequest;

  try {
    const data = await api.listTenants(body);
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
| **status** | `ACTIVE`, `SUSPENDED`, `DELETED` |  | [Optional] [Defaults to `undefined`] [Enum: ACTIVE, SUSPENDED, DELETED] |
| **includeDeleted** | `boolean` |  | [Optional] [Defaults to `false`] |

### Return type

[**Array&lt;TenantResponse&gt;**](TenantResponse.md)

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


## updateTenant

> TenantResponse updateTenant(tenantId, updateTenantRequest)



### Example

```ts
import {
  Configuration,
  AdminTenantControllerApi,
} from '';
import type { UpdateTenantOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminTenantControllerApi(config);

  const body = {
    // string
    tenantId: tenantId_example,
    // UpdateTenantRequest
    updateTenantRequest: ...,
  } satisfies UpdateTenantOperationRequest;

  try {
    const data = await api.updateTenant(body);
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
| **updateTenantRequest** | [UpdateTenantRequest](UpdateTenantRequest.md) |  | |

### Return type

[**TenantResponse**](TenantResponse.md)

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

