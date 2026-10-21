# AdminAccountControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**createAccount**](AdminAccountControllerApi.md#createaccountoperation) | **POST** /api/v1/admin/accounts |  |
| [**getAccount**](AdminAccountControllerApi.md#getaccount) | **GET** /api/v1/admin/accounts/{accountId} |  |
| [**listAccountAuditLogs**](AdminAccountControllerApi.md#listaccountauditlogs) | **GET** /api/v1/admin/accounts/{accountId}/audit |  |
| [**listAccounts**](AdminAccountControllerApi.md#listaccounts) | **GET** /api/v1/admin/accounts |  |
| [**listOwnedAgents**](AdminAccountControllerApi.md#listownedagents) | **GET** /api/v1/admin/accounts/{accountId}/agents |  |
| [**reassignTenant**](AdminAccountControllerApi.md#reassigntenantoperation) | **POST** /api/v1/admin/accounts/{accountId}/tenant |  |
| [**resetCredentials**](AdminAccountControllerApi.md#resetcredentialsoperation) | **POST** /api/v1/admin/accounts/{accountId}/reset-credentials |  |
| [**updateAccount**](AdminAccountControllerApi.md#updateaccountoperation) | **PUT** /api/v1/admin/accounts/{accountId} |  |



## createAccount

> AccountAdminResponse createAccount(createAccountRequest)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { CreateAccountOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // CreateAccountRequest
    createAccountRequest: ...,
  } satisfies CreateAccountOperationRequest;

  try {
    const data = await api.createAccount(body);
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
| **createAccountRequest** | [CreateAccountRequest](CreateAccountRequest.md) |  | |

### Return type

[**AccountAdminResponse**](AccountAdminResponse.md)

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


## getAccount

> AccountAdminResponse getAccount(accountId)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { GetAccountRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
  } satisfies GetAccountRequest;

  try {
    const data = await api.getAccount(body);
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

[**AccountAdminResponse**](AccountAdminResponse.md)

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


## listAccountAuditLogs

> Array&lt;TenantAuditResponse&gt; listAccountAuditLogs(accountId)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { ListAccountAuditLogsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
  } satisfies ListAccountAuditLogsRequest;

  try {
    const data = await api.listAccountAuditLogs(body);
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


## listAccounts

> Array&lt;AccountAdminResponse&gt; listAccounts(tenantId, kind, profile, active, ownerAccountId)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { ListAccountsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
    // 'HUMAN' | 'AGENT' | 'SERVICE' (optional)
    kind: kind_example,
    // 'HUMAN_SOLO' | 'HUMAN_TEAM' | 'AGENT' | 'SERVICE' | 'UNLIMITED' (optional)
    profile: profile_example,
    // boolean (optional)
    active: true,
    // string (optional)
    ownerAccountId: ownerAccountId_example,
  } satisfies ListAccountsRequest;

  try {
    const data = await api.listAccounts(body);
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
| **kind** | `HUMAN`, `AGENT`, `SERVICE` |  | [Optional] [Defaults to `undefined`] [Enum: HUMAN, AGENT, SERVICE] |
| **profile** | `HUMAN_SOLO`, `HUMAN_TEAM`, `AGENT`, `SERVICE`, `UNLIMITED` |  | [Optional] [Defaults to `undefined`] [Enum: HUMAN_SOLO, HUMAN_TEAM, AGENT, SERVICE, UNLIMITED] |
| **active** | `boolean` |  | [Optional] [Defaults to `undefined`] |
| **ownerAccountId** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Array&lt;AccountAdminResponse&gt;**](AccountAdminResponse.md)

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


## listOwnedAgents

> Array&lt;OwnedAgentSummaryDto&gt; listOwnedAgents(accountId)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { ListOwnedAgentsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
  } satisfies ListOwnedAgentsRequest;

  try {
    const data = await api.listOwnedAgents(body);
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

[**Array&lt;OwnedAgentSummaryDto&gt;**](OwnedAgentSummaryDto.md)

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


## reassignTenant

> AccountAdminResponse reassignTenant(accountId, reassignTenantRequest)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { ReassignTenantOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
    // ReassignTenantRequest
    reassignTenantRequest: ...,
  } satisfies ReassignTenantOperationRequest;

  try {
    const data = await api.reassignTenant(body);
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
| **reassignTenantRequest** | [ReassignTenantRequest](ReassignTenantRequest.md) |  | |

### Return type

[**AccountAdminResponse**](AccountAdminResponse.md)

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


## resetCredentials

> ResetCredentialsResponse resetCredentials(accountId, resetCredentialsRequest)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { ResetCredentialsOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
    // ResetCredentialsRequest (optional)
    resetCredentialsRequest: ...,
  } satisfies ResetCredentialsOperationRequest;

  try {
    const data = await api.resetCredentials(body);
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
| **resetCredentialsRequest** | [ResetCredentialsRequest](ResetCredentialsRequest.md) |  | [Optional] |

### Return type

[**ResetCredentialsResponse**](ResetCredentialsResponse.md)

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


## updateAccount

> AccountAdminResponse updateAccount(accountId, updateAccountRequest)



### Example

```ts
import {
  Configuration,
  AdminAccountControllerApi,
} from '';
import type { UpdateAccountOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAccountControllerApi(config);

  const body = {
    // string
    accountId: accountId_example,
    // UpdateAccountRequest
    updateAccountRequest: ...,
  } satisfies UpdateAccountOperationRequest;

  try {
    const data = await api.updateAccount(body);
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
| **updateAccountRequest** | [UpdateAccountRequest](UpdateAccountRequest.md) |  | |

### Return type

[**AccountAdminResponse**](AccountAdminResponse.md)

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

