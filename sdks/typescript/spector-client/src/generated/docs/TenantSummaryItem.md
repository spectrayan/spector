
# TenantSummaryItem


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`displayName` | string
`plan` | string
`status` | string
`accountCount` | number
`namespaceCount` | number
`totalMemories` | number
`storageBytes` | number
`recallOpsPerSec` | number
`rememberOpsPerSec` | number
`latencyP95Ms` | number
`totalTokens` | number
`estimatedCostUsd` | number
`quotaBreachCount` | number

## Example

```typescript
import type { TenantSummaryItem } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "displayName": null,
  "plan": null,
  "status": null,
  "accountCount": null,
  "namespaceCount": null,
  "totalMemories": null,
  "storageBytes": null,
  "recallOpsPerSec": null,
  "rememberOpsPerSec": null,
  "latencyP95Ms": null,
  "totalTokens": null,
  "estimatedCostUsd": null,
  "quotaBreachCount": null,
} satisfies TenantSummaryItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantSummaryItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


