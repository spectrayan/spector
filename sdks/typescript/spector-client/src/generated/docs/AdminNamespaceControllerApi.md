# AdminNamespaceControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**archiveNamespace**](AdminNamespaceControllerApi.md#archivenamespace) | **POST** /api/v1/admin/namespaces/{namespaceId}/archive |  |
| [**eraseNamespace**](AdminNamespaceControllerApi.md#erasenamespaceoperation) | **POST** /api/v1/admin/namespaces/{namespaceId}/erase |  |
| [**getNamespace1**](AdminNamespaceControllerApi.md#getnamespace1) | **GET** /api/v1/admin/namespaces/{namespaceId} |  |
| [**listGrants1**](AdminNamespaceControllerApi.md#listgrants1) | **GET** /api/v1/admin/namespaces/{namespaceId}/grants |  |
| [**listNamespaceAuditLogs**](AdminNamespaceControllerApi.md#listnamespaceauditlogs) | **GET** /api/v1/admin/namespaces/{namespaceId}/audit |  |
| [**listNamespaces1**](AdminNamespaceControllerApi.md#listnamespaces1) | **GET** /api/v1/admin/namespaces |  |
| [**restoreNamespace**](AdminNamespaceControllerApi.md#restorenamespace) | **POST** /api/v1/admin/namespaces/{namespaceId}/restore |  |
| [**revokeGrant1**](AdminNamespaceControllerApi.md#revokegrant1) | **DELETE** /api/v1/admin/namespaces/{namespaceId}/grants/{grantId} |  |
| [**setLegalHold1**](AdminNamespaceControllerApi.md#setlegalhold1) | **POST** /api/v1/admin/namespaces/{namespaceId}/legal-hold |  |
| [**tombstoneNamespace**](AdminNamespaceControllerApi.md#tombstonenamespace) | **DELETE** /api/v1/admin/namespaces/{namespaceId} |  |
| [**transferOwnership**](AdminNamespaceControllerApi.md#transferownershipoperation) | **POST** /api/v1/admin/namespaces/{namespaceId}/transfer-ownership |  |
| [**updateQuotas**](AdminNamespaceControllerApi.md#updatequotas) | **PUT** /api/v1/admin/namespaces/{namespaceId}/quotas |  |



## archiveNamespace

> AdminNamespaceResponse archiveNamespace(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { ArchiveNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies ArchiveNamespaceRequest;

  try {
    const data = await api.archiveNamespace(body);
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

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## eraseNamespace

> ErasureAuditReport eraseNamespace(namespaceId, eraseNamespaceRequest)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { EraseNamespaceOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
    // EraseNamespaceRequest (optional)
    eraseNamespaceRequest: ...,
  } satisfies EraseNamespaceOperationRequest;

  try {
    const data = await api.eraseNamespace(body);
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
| **eraseNamespaceRequest** | [EraseNamespaceRequest](EraseNamespaceRequest.md) |  | [Optional] |

### Return type

[**ErasureAuditReport**](ErasureAuditReport.md)

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


## getNamespace1

> AdminNamespaceResponse getNamespace1(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { GetNamespace1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies GetNamespace1Request;

  try {
    const data = await api.getNamespace1(body);
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

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## listGrants1

> Array&lt;AdminNamespaceGrantResponse&gt; listGrants1(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { ListGrants1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies ListGrants1Request;

  try {
    const data = await api.listGrants1(body);
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

[**Array&lt;AdminNamespaceGrantResponse&gt;**](AdminNamespaceGrantResponse.md)

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


## listNamespaceAuditLogs

> Array&lt;TenantAuditResponse&gt; listNamespaceAuditLogs(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { ListNamespaceAuditLogsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies ListNamespaceAuditLogsRequest;

  try {
    const data = await api.listNamespaceAuditLogs(body);
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


## listNamespaces1

> Array&lt;AdminNamespaceResponse&gt; listNamespaces1(tenantId, ownerAccountId, status, type, legalHold, includeTombstoned)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { ListNamespaces1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
    // string (optional)
    ownerAccountId: ownerAccountId_example,
    // 'ACTIVE' | 'TOMBSTONED' | 'ARCHIVED' | 'LEGAL_HOLD' (optional)
    status: status_example,
    // 'DEFAULT' | 'PROJECT' | 'AGENT' | 'SHARED' | 'ARCHIVE' (optional)
    type: type_example,
    // boolean (optional)
    legalHold: true,
    // boolean (optional)
    includeTombstoned: true,
  } satisfies ListNamespaces1Request;

  try {
    const data = await api.listNamespaces1(body);
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
| **ownerAccountId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **status** | `ACTIVE`, `TOMBSTONED`, `ARCHIVED`, `LEGAL_HOLD` |  | [Optional] [Defaults to `undefined`] [Enum: ACTIVE, TOMBSTONED, ARCHIVED, LEGAL_HOLD] |
| **type** | `DEFAULT`, `PROJECT`, `AGENT`, `SHARED`, `ARCHIVE` |  | [Optional] [Defaults to `undefined`] [Enum: DEFAULT, PROJECT, AGENT, SHARED, ARCHIVE] |
| **legalHold** | `boolean` |  | [Optional] [Defaults to `undefined`] |
| **includeTombstoned** | `boolean` |  | [Optional] [Defaults to `false`] |

### Return type

[**Array&lt;AdminNamespaceResponse&gt;**](AdminNamespaceResponse.md)

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


## restoreNamespace

> AdminNamespaceResponse restoreNamespace(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { RestoreNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies RestoreNamespaceRequest;

  try {
    const data = await api.restoreNamespace(body);
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

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## revokeGrant1

> revokeGrant1(namespaceId, grantId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { RevokeGrant1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
    // string
    grantId: grantId_example,
  } satisfies RevokeGrant1Request;

  try {
    const data = await api.revokeGrant1(body);
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


## setLegalHold1

> AdminNamespaceResponse setLegalHold1(namespaceId, namespaceLegalHoldRequest)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { SetLegalHold1Request } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
    // NamespaceLegalHoldRequest
    namespaceLegalHoldRequest: ...,
  } satisfies SetLegalHold1Request;

  try {
    const data = await api.setLegalHold1(body);
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
| **namespaceLegalHoldRequest** | [NamespaceLegalHoldRequest](NamespaceLegalHoldRequest.md) |  | |

### Return type

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## tombstoneNamespace

> AdminNamespaceResponse tombstoneNamespace(namespaceId)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { TombstoneNamespaceRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
  } satisfies TombstoneNamespaceRequest;

  try {
    const data = await api.tombstoneNamespace(body);
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

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## transferOwnership

> AdminNamespaceResponse transferOwnership(namespaceId, transferOwnershipRequest)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { TransferOwnershipOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
    // TransferOwnershipRequest
    transferOwnershipRequest: ...,
  } satisfies TransferOwnershipOperationRequest;

  try {
    const data = await api.transferOwnership(body);
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
| **transferOwnershipRequest** | [TransferOwnershipRequest](TransferOwnershipRequest.md) |  | |

### Return type

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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


## updateQuotas

> AdminNamespaceResponse updateQuotas(namespaceId, updateNamespaceQuotasRequest)



### Example

```ts
import {
  Configuration,
  AdminNamespaceControllerApi,
} from '';
import type { UpdateQuotasRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminNamespaceControllerApi(config);

  const body = {
    // string
    namespaceId: namespaceId_example,
    // UpdateNamespaceQuotasRequest
    updateNamespaceQuotasRequest: ...,
  } satisfies UpdateQuotasRequest;

  try {
    const data = await api.updateQuotas(body);
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
| **updateNamespaceQuotasRequest** | [UpdateNamespaceQuotasRequest](UpdateNamespaceQuotasRequest.md) |  | |

### Return type

[**AdminNamespaceResponse**](AdminNamespaceResponse.md)

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

