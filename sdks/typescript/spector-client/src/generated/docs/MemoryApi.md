# MemoryApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**browseMemories**](MemoryApi.md#browsememories) | **POST** /api/v1/memory/browse | Tag-based memory browsing without vector search |
| [**bulkForgetMemories**](MemoryApi.md#bulkforgetmemories) | **POST** /api/v1/memory/bulk/forget | Bulk forget memories by ID list |
| [**bulkReinforceMemories**](MemoryApi.md#bulkreinforcememories) | **POST** /api/v1/memory/bulk/reinforce | Bulk reinforce memories by ID list |
| [**bulkSuppressMemories**](MemoryApi.md#bulksuppressmemories) | **POST** /api/v1/memory/bulk/suppress | Bulk suppress or unsuppress memories by ID list |
| [**consolidateMemories**](MemoryApi.md#consolidatememories) | **POST** /api/v1/memory/consolidate | Trigger manual memory consolidation |
| [**enrichGraph**](MemoryApi.md#enrichgraph) | **POST** /api/v1/memory/enrich-graph | Trigger asynchronous offline graph enrichment in the background |
| [**federatedRecallMemories**](MemoryApi.md#federatedrecallmemories) | **POST** /api/v1/memory/federated-recall | Cross-rememberer federated recall |
| [**forgetMemory**](MemoryApi.md#forgetmemory) | **DELETE** /api/v1/memory/{id} | Tombstone (forget) a memory by ID |
| [**getConsolidationDiff**](MemoryApi.md#getconsolidationdiff) | **GET** /api/v1/memory/consolidation/diff | Latest consolidation snapshot diff |
| [**getDecayCurve**](MemoryApi.md#getdecaycurve) | **GET** /api/v1/memory/diagnostics/decay | Ebbinghaus forgetting and LTP retention decay curve |
| [**getEnrichmentStatus**](MemoryApi.md#getenrichmentstatus) | **GET** /api/v1/memory/enrich-graph/status | Real-time telemetry for offline graph enrichment daemon |
| [**getGraphOverview**](MemoryApi.md#getgraphoverview) | **GET** /api/v1/memory/graph/overview | Sampled overview of associative memory graph |
| [**getHardwareInfo**](MemoryApi.md#gethardwareinfo) | **GET** /api/v1/memory/hardware | System SIMD Vector API and hardware capabilities |
| [**getLiveMetrics**](MemoryApi.md#getlivemetrics) | **GET** /api/v1/memory/metrics/live | Recent live rolling ops/sec metrics history |
| [**getMemoryById**](MemoryApi.md#getmemorybyid) | **GET** /api/v1/memory/{id} | Retrieve a single memory by ID |
| [**getMemoryDiagnostics**](MemoryApi.md#getmemorydiagnostics) | **GET** /api/v1/memory/diagnostics | Diagnostics snapshot (tier counts, allocations) |
| [**getMemoryGraph**](MemoryApi.md#getmemorygraph) | **GET** /api/v1/memory/{id}/graph | Hebbian, Temporal, and Entity graph neighborhood for a memory |
| [**getMemoryStats**](MemoryApi.md#getmemorystats) | **GET** /api/v1/memory/stats | Memory health statistics |
| [**getMemoryStatus**](MemoryApi.md#getmemorystatus) | **GET** /api/v1/memory/status | Cognitive memory status and tier counts |
| [**getMemoryTable**](MemoryApi.md#getmemorytable) | **GET** /api/v1/memory/table | Paginated memory table view for UI and exploration |
| [**getMemoryVector**](MemoryApi.md#getmemoryvector) | **GET** /api/v1/memory/{id}/vector | Retrieve INT8 quantized embedding vector for a memory |
| [**getReflectProgress**](MemoryApi.md#getreflectprogress) | **GET** /api/v1/memory/reflect/progress/{sweepId} | Poll reflection sweep progress telemetry |
| [**getScoringStats**](MemoryApi.md#getscoringstats) | **GET** /api/v1/memory/stats/scoring | Memory scoring metrics averages |
| [**getTopologyStats**](MemoryApi.md#gettopologystats) | **GET** /api/v1/memory/topology-stats | Topology statistics of entities and relationships |
| [**getVectorSpaceProjection**](MemoryApi.md#getvectorspaceprojection) | **GET** /api/v1/memory/vector-space/projection | 3D PCA vector space embedding projection |
| [**ingestMemoryFile**](MemoryApi.md#ingestmemoryfile) | **POST** /api/v1/memory/ingest-file | Ingest a file into memory asynchronously |
| [**recallMemories**](MemoryApi.md#recallmemories) | **POST** /api/v1/memory/recall | Cognitive recall with biological scoring |
| [**reextractGraph**](MemoryApi.md#reextractgraph) | **POST** /api/v1/memory/reextract-graph | Trigger full re-extraction of entities and relationships |
| [**reflectMemories**](MemoryApi.md#reflectmemories) | **POST** /api/v1/memory/reflect | Trigger sleep consolidation (reflect) sweep |
| [**reinforceMemory**](MemoryApi.md#reinforcememory) | **POST** /api/v1/memory/{id}/reinforce | Reinforce memory via Long-Term Potentiation (LTP) |
| [**rememberMemory**](MemoryApi.md#remembermemory) | **POST** /api/v1/memory/remember | Remember a memory asynchronously with cognitive scoring hints |
| [**resolveMemory**](MemoryApi.md#resolvememory) | **POST** /api/v1/memory/{id}/resolve | Resolve or unresolve a memory (Zeigarnik closure) |
| [**searchMemories**](MemoryApi.md#searchmemories) | **POST** /api/v1/memory/search | Semantic similarity search |
| [**storeMemory**](MemoryApi.md#storememory) | **POST** /api/v1/memory | Store a cognitive memory synchronously |
| [**suppressMemory**](MemoryApi.md#suppressmemory) | **POST** /api/v1/memory/{id}/suppress | Suppress or unsuppress a memory from recall |
| [**updateMemory**](MemoryApi.md#updatememoryoperation) | **PUT** /api/v1/memory/{id} | Update an existing memory by ID |
| [**vacuumMemories**](MemoryApi.md#vacuummemories) | **POST** /api/v1/memory/vacuum | Trigger vacuum compaction for a tier |



## browseMemories

> Array&lt;BrowseResult&gt; browseMemories(browseRequest)

Tag-based memory browsing without vector search

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { BrowseMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // BrowseRequest
    browseRequest: ...,
  } satisfies BrowseMemoriesRequest;

  try {
    const data = await api.browseMemories(body);
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
| **browseRequest** | [BrowseRequest](BrowseRequest.md) |  | |

### Return type

[**Array&lt;BrowseResult&gt;**](BrowseResult.md)

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


## bulkForgetMemories

> { [key: string]: any | null; } bulkForgetMemories(requestBody)

Bulk forget memories by ID list

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { BulkForgetMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // Array<string>
    requestBody: ...,
  } satisfies BulkForgetMemoriesRequest;

  try {
    const data = await api.bulkForgetMemories(body);
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
| **requestBody** | `Array<string>` |  | |

### Return type

**{ [key: string]: any | null; }**

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


## bulkReinforceMemories

> { [key: string]: any | null; } bulkReinforceMemories(requestBody, valence)

Bulk reinforce memories by ID list

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { BulkReinforceMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // Array<string>
    requestBody: ...,
    // number (optional)
    valence: 56,
  } satisfies BulkReinforceMemoriesRequest;

  try {
    const data = await api.bulkReinforceMemories(body);
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
| **requestBody** | `Array<string>` |  | |
| **valence** | `number` |  | [Optional] [Defaults to `0`] |

### Return type

**{ [key: string]: any | null; }**

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


## bulkSuppressMemories

> { [key: string]: any | null; } bulkSuppressMemories(requestBody, action)

Bulk suppress or unsuppress memories by ID list

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { BulkSuppressMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // Array<string>
    requestBody: ...,
    // string (optional)
    action: action_example,
  } satisfies BulkSuppressMemoriesRequest;

  try {
    const data = await api.bulkSuppressMemories(body);
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
| **requestBody** | `Array<string>` |  | |
| **action** | `string` |  | [Optional] [Defaults to `&#39;SUPPRESS&#39;`] |

### Return type

**{ [key: string]: any | null; }**

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


## consolidateMemories

> consolidateMemories()

Trigger manual memory consolidation

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ConsolidateMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.consolidateMemories();
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


## enrichGraph

> EnrichmentTriggerResponse enrichGraph(limit)

Trigger asynchronous offline graph enrichment in the background

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { EnrichGraphRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // number (optional)
    limit: 56,
  } satisfies EnrichGraphRequest;

  try {
    const data = await api.enrichGraph(body);
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
| **limit** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**EnrichmentTriggerResponse**](EnrichmentTriggerResponse.md)

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


## federatedRecallMemories

> FederatedRecallResponse federatedRecallMemories(federatedRecallRequest)

Cross-rememberer federated recall

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { FederatedRecallMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // FederatedRecallRequest
    federatedRecallRequest: ...,
  } satisfies FederatedRecallMemoriesRequest;

  try {
    const data = await api.federatedRecallMemories(body);
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
| **federatedRecallRequest** | [FederatedRecallRequest](FederatedRecallRequest.md) |  | |

### Return type

[**FederatedRecallResponse**](FederatedRecallResponse.md)

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


## forgetMemory

> { [key: string]: string; } forgetMemory(id)

Tombstone (forget) a memory by ID

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ForgetMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies ForgetMemoryRequest;

  try {
    const data = await api.forgetMemory(body);
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

**{ [key: string]: string; }**

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


## getConsolidationDiff

> Array&lt;{ [key: string]: any | null; }&gt; getConsolidationDiff()

Latest consolidation snapshot diff

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetConsolidationDiffRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getConsolidationDiff();
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

**Array<{ [key: string]: any | null; }>**

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


## getDecayCurve

> Array&lt;{ [key: string]: any | null; }&gt; getDecayCurve()

Ebbinghaus forgetting and LTP retention decay curve

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetDecayCurveRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getDecayCurve();
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

**Array<{ [key: string]: any | null; }>**

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


## getEnrichmentStatus

> EnrichmentStatusResponse getEnrichmentStatus()

Real-time telemetry for offline graph enrichment daemon

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetEnrichmentStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getEnrichmentStatus();
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

[**EnrichmentStatusResponse**](EnrichmentStatusResponse.md)

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


## getGraphOverview

> MemoryGraphResponse getGraphOverview(maxNodes)

Sampled overview of associative memory graph

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetGraphOverviewRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // number (optional)
    maxNodes: 56,
  } satisfies GetGraphOverviewRequest;

  try {
    const data = await api.getGraphOverview(body);
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
| **maxNodes** | `number` |  | [Optional] [Defaults to `100`] |

### Return type

[**MemoryGraphResponse**](MemoryGraphResponse.md)

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


## getHardwareInfo

> { [key: string]: any | null; } getHardwareInfo()

System SIMD Vector API and hardware capabilities

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetHardwareInfoRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getHardwareInfo();
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


## getLiveMetrics

> Array&lt;{ [key: string]: any | null; }&gt; getLiveMetrics()

Recent live rolling ops/sec metrics history

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetLiveMetricsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getLiveMetrics();
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

**Array<{ [key: string]: any | null; }>**

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


## getMemoryById

> MemoryTableRow getMemoryById(id)

Retrieve a single memory by ID

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryByIdRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies GetMemoryByIdRequest;

  try {
    const data = await api.getMemoryById(body);
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

[**MemoryTableRow**](MemoryTableRow.md)

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


## getMemoryDiagnostics

> { [key: string]: any | null; } getMemoryDiagnostics()

Diagnostics snapshot (tier counts, allocations)

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryDiagnosticsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getMemoryDiagnostics();
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


## getMemoryGraph

> MemoryGraphResponse getMemoryGraph(id, depth)

Hebbian, Temporal, and Entity graph neighborhood for a memory

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryGraphRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
    // number (optional)
    depth: 56,
  } satisfies GetMemoryGraphRequest;

  try {
    const data = await api.getMemoryGraph(body);
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
| **depth** | `number` |  | [Optional] [Defaults to `2`] |

### Return type

[**MemoryGraphResponse**](MemoryGraphResponse.md)

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


## getMemoryStats

> MemoryStats getMemoryStats()

Memory health statistics

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryStatsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getMemoryStats();
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

[**MemoryStats**](MemoryStats.md)

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


## getMemoryStatus

> MemoryStatusResponse getMemoryStatus()

Cognitive memory status and tier counts

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryStatusRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getMemoryStatus();
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

[**MemoryStatusResponse**](MemoryStatusResponse.md)

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


## getMemoryTable

> MemoryTableResponse getMemoryTable(page, pageSize, tier, tombstoned)

Paginated memory table view for UI and exploration

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryTableRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // number (optional)
    page: 56,
    // number (optional)
    pageSize: 56,
    // string (optional)
    tier: tier_example,
    // boolean (optional)
    tombstoned: true,
  } satisfies GetMemoryTableRequest;

  try {
    const data = await api.getMemoryTable(body);
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
| **page** | `number` |  | [Optional] [Defaults to `0`] |
| **pageSize** | `number` |  | [Optional] [Defaults to `50`] |
| **tier** | `string` |  | [Optional] [Defaults to `undefined`] |
| **tombstoned** | `boolean` |  | [Optional] [Defaults to `false`] |

### Return type

[**MemoryTableResponse**](MemoryTableResponse.md)

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


## getMemoryVector

> MemoryVectorResponse getMemoryVector(id)

Retrieve INT8 quantized embedding vector for a memory

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetMemoryVectorRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies GetMemoryVectorRequest;

  try {
    const data = await api.getMemoryVector(body);
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

[**MemoryVectorResponse**](MemoryVectorResponse.md)

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


## getReflectProgress

> ReflectSweepProgress getReflectProgress(sweepId)

Poll reflection sweep progress telemetry

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetReflectProgressRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    sweepId: sweepId_example,
  } satisfies GetReflectProgressRequest;

  try {
    const data = await api.getReflectProgress(body);
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
| **sweepId** | `string` |  | [Defaults to `undefined`] |

### Return type

[**ReflectSweepProgress**](ReflectSweepProgress.md)

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


## getScoringStats

> ScoringStats getScoringStats()

Memory scoring metrics averages

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetScoringStatsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getScoringStats();
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

[**ScoringStats**](ScoringStats.md)

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


## getTopologyStats

> TopologyStatsResponse getTopologyStats()

Topology statistics of entities and relationships

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetTopologyStatsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getTopologyStats();
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

[**TopologyStatsResponse**](TopologyStatsResponse.md)

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


## getVectorSpaceProjection

> ProjectionResult getVectorSpaceProjection()

3D PCA vector space embedding projection

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { GetVectorSpaceProjectionRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  try {
    const data = await api.getVectorSpaceProjection();
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

[**ProjectionResult**](ProjectionResult.md)

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


## ingestMemoryFile

> AcceptedResponse ingestMemoryFile(file, tier, source)

Ingest a file into memory asynchronously

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { IngestMemoryFileRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // Blob
    file: BINARY_DATA_HERE,
    // string (optional)
    tier: tier_example,
    // string (optional)
    source: source_example,
  } satisfies IngestMemoryFileRequest;

  try {
    const data = await api.ingestMemoryFile(body);
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
| **file** | `Blob` |  | [Defaults to `undefined`] |
| **tier** | `string` |  | [Optional] [Defaults to `&#39;SEMANTIC&#39;`] |
| **source** | `string` |  | [Optional] [Defaults to `&#39;OBSERVED&#39;`] |

### Return type

[**AcceptedResponse**](AcceptedResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `multipart/form-data`
- **Accept**: `*/*`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## recallMemories

> Array&lt;RecallResult&gt; recallMemories(recallRequest)

Cognitive recall with biological scoring

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { RecallMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // RecallRequest
    recallRequest: ...,
  } satisfies RecallMemoriesRequest;

  try {
    const data = await api.recallMemories(body);
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
| **recallRequest** | [RecallRequest](RecallRequest.md) |  | |

### Return type

[**Array&lt;RecallResult&gt;**](RecallResult.md)

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


## reextractGraph

> EnrichmentTriggerResponse reextractGraph(limit)

Trigger full re-extraction of entities and relationships

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ReextractGraphRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // number (optional)
    limit: 56,
  } satisfies ReextractGraphRequest;

  try {
    const data = await api.reextractGraph(body);
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
| **limit** | `number` |  | [Optional] [Defaults to `50`] |

### Return type

[**EnrichmentTriggerResponse**](EnrichmentTriggerResponse.md)

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


## reflectMemories

> ReflectResponse reflectMemories(sweepId, sessionLimit, sessionIdAfter, from, to, consolidationOnly, reflectRequest)

Trigger sleep consolidation (reflect) sweep

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ReflectMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string (optional)
    sweepId: sweepId_example,
    // number (optional)
    sessionLimit: 56,
    // number (optional)
    sessionIdAfter: 789,
    // number (optional)
    from: 789,
    // number (optional)
    to: 789,
    // boolean (optional)
    consolidationOnly: true,
    // ReflectRequest (optional)
    reflectRequest: ...,
  } satisfies ReflectMemoriesRequest;

  try {
    const data = await api.reflectMemories(body);
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
| **sweepId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **sessionLimit** | `number` |  | [Optional] [Defaults to `undefined`] |
| **sessionIdAfter** | `number` |  | [Optional] [Defaults to `undefined`] |
| **from** | `number` |  | [Optional] [Defaults to `undefined`] |
| **to** | `number` |  | [Optional] [Defaults to `undefined`] |
| **consolidationOnly** | `boolean` |  | [Optional] [Defaults to `undefined`] |
| **reflectRequest** | [ReflectRequest](ReflectRequest.md) |  | [Optional] |

### Return type

[**ReflectResponse**](ReflectResponse.md)

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


## reinforceMemory

> { [key: string]: any | null; } reinforceMemory(id, reinforceByIdRequest)

Reinforce memory via Long-Term Potentiation (LTP)

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ReinforceMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
    // ReinforceByIdRequest (optional)
    reinforceByIdRequest: ...,
  } satisfies ReinforceMemoryRequest;

  try {
    const data = await api.reinforceMemory(body);
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
| **reinforceByIdRequest** | [ReinforceByIdRequest](ReinforceByIdRequest.md) |  | [Optional] |

### Return type

**{ [key: string]: any | null; }**

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


## rememberMemory

> AcceptedResponse rememberMemory(rememberRequest)

Remember a memory asynchronously with cognitive scoring hints

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { RememberMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // RememberRequest
    rememberRequest: ...,
  } satisfies RememberMemoryRequest;

  try {
    const data = await api.rememberMemory(body);
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
| **rememberRequest** | [RememberRequest](RememberRequest.md) |  | |

### Return type

[**AcceptedResponse**](AcceptedResponse.md)

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


## resolveMemory

> { [key: string]: any | null; } resolveMemory(id, resolveRequest)

Resolve or unresolve a memory (Zeigarnik closure)

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { ResolveMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
    // ResolveRequest (optional)
    resolveRequest: ...,
  } satisfies ResolveMemoryRequest;

  try {
    const data = await api.resolveMemory(body);
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
| **resolveRequest** | [ResolveRequest](ResolveRequest.md) |  | [Optional] |

### Return type

**{ [key: string]: any | null; }**

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


## searchMemories

> Array&lt;SearchResult&gt; searchMemories(searchRequest)

Semantic similarity search

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { SearchMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // SearchRequest
    searchRequest: ...,
  } satisfies SearchMemoriesRequest;

  try {
    const data = await api.searchMemories(body);
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
| **searchRequest** | [SearchRequest](SearchRequest.md) |  | |

### Return type

[**Array&lt;SearchResult&gt;**](SearchResult.md)

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


## storeMemory

> StoreResponse storeMemory(storeRequest)

Store a cognitive memory synchronously

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { StoreMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // StoreRequest
    storeRequest: ...,
  } satisfies StoreMemoryRequest;

  try {
    const data = await api.storeMemory(body);
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
| **storeRequest** | [StoreRequest](StoreRequest.md) |  | |

### Return type

[**StoreResponse**](StoreResponse.md)

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


## suppressMemory

> { [key: string]: string; } suppressMemory(id, suppressRequest)

Suppress or unsuppress a memory from recall

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { SuppressMemoryRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
    // SuppressRequest (optional)
    suppressRequest: ...,
  } satisfies SuppressMemoryRequest;

  try {
    const data = await api.suppressMemory(body);
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
| **suppressRequest** | [SuppressRequest](SuppressRequest.md) |  | [Optional] |

### Return type

**{ [key: string]: string; }**

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


## updateMemory

> string updateMemory(id, updateMemoryRequest)

Update an existing memory by ID

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { UpdateMemoryOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // string
    id: id_example,
    // UpdateMemoryRequest
    updateMemoryRequest: ...,
  } satisfies UpdateMemoryOperationRequest;

  try {
    const data = await api.updateMemory(body);
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
| **updateMemoryRequest** | [UpdateMemoryRequest](UpdateMemoryRequest.md) |  | |

### Return type

**string**

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


## vacuumMemories

> CompactionResult vacuumMemories(vacuumRequest)

Trigger vacuum compaction for a tier

### Example

```ts
import {
  Configuration,
  MemoryApi,
} from '';
import type { VacuumMemoriesRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MemoryApi(config);

  const body = {
    // VacuumRequest (optional)
    vacuumRequest: ...,
  } satisfies VacuumMemoriesRequest;

  try {
    const data = await api.vacuumMemories(body);
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
| **vacuumRequest** | [VacuumRequest](VacuumRequest.md) |  | [Optional] |

### Return type

[**CompactionResult**](CompactionResult.md)

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

