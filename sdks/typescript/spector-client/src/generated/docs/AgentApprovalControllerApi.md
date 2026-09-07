# AgentApprovalControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**approve**](AgentApprovalControllerApi.md#approve) | **POST** /api/v1/agent/approvals/{id}/approve |  |
| [**cancel**](AgentApprovalControllerApi.md#cancel) | **POST** /api/v1/agent/approvals/{id}/cancel |  |
| [**getApproval**](AgentApprovalControllerApi.md#getapproval) | **GET** /api/v1/agent/approvals/{id} |  |
| [**listApprovals**](AgentApprovalControllerApi.md#listapprovals) | **GET** /api/v1/agent/approvals |  |
| [**modify**](AgentApprovalControllerApi.md#modify) | **POST** /api/v1/agent/approvals/{id}/modify |  |
| [**reject**](AgentApprovalControllerApi.md#reject) | **POST** /api/v1/agent/approvals/{id}/reject |  |



## approve

> object approve(id)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { ApproveRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies ApproveRequest;

  try {
    const data = await api.approve(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

**object**

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


## cancel

> object cancel(id, approvalDecisionRequest)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { CancelRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // string
    id: id_example,
    // ApprovalDecisionRequest (optional)
    approvalDecisionRequest: ...,
  } satisfies CancelRequest;

  try {
    const data = await api.cancel(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **approvalDecisionRequest** | [ApprovalDecisionRequest](ApprovalDecisionRequest.md) |  | [Optional] |

### Return type

**object**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getApproval

> AgentActionApproval getApproval(id)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { GetApprovalRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies GetApprovalRequest;

  try {
    const data = await api.getApproval(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**AgentActionApproval**](AgentActionApproval.md)

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


## listApprovals

> Array&lt;AgentActionApproval&gt; listApprovals(pendingOnly, limit)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { ListApprovalsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // boolean (optional)
    pendingOnly: true,
    // number (optional)
    limit: 56,
  } satisfies ListApprovalsRequest;

  try {
    const data = await api.listApprovals(body);
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
| **pendingOnly** | `boolean` |  | [Optional] [Defaults to `false`] |
| **limit** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**Array&lt;AgentActionApproval&gt;**](AgentActionApproval.md)

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


## modify

> object modify(id, approvalDecisionRequest)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { ModifyRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // string
    id: id_example,
    // ApprovalDecisionRequest
    approvalDecisionRequest: ...,
  } satisfies ModifyRequest;

  try {
    const data = await api.modify(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **approvalDecisionRequest** | [ApprovalDecisionRequest](ApprovalDecisionRequest.md) |  | |

### Return type

**object**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## reject

> object reject(id, approvalDecisionRequest)



### Example

```ts
import {
  Configuration,
  AgentApprovalControllerApi,
} from '';
import type { RejectRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AgentApprovalControllerApi(config);

  const body = {
    // string
    id: id_example,
    // ApprovalDecisionRequest (optional)
    approvalDecisionRequest: ...,
  } satisfies RejectRequest;

  try {
    const data = await api.reject(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **approvalDecisionRequest** | [ApprovalDecisionRequest](ApprovalDecisionRequest.md) |  | [Optional] |

### Return type

**object**

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

