
# TenantAnalyticsRollup


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`displayName` | string
`plan` | string
`status` | string
`accountCount` | number
`humanCount` | number
`agentCount` | number
`namespaceCount` | number
`storageBytes` | number
`tiers` | [TierBreakdown](TierBreakdown.md)
`growth` | [GrowthStats](GrowthStats.md)
`throughput` | [ThroughputAndLatency](ThroughputAndLatency.md)
`consolidation` | [ConsolidationAndDreamHealth](ConsolidationAndDreamHealth.md)
`tokenUsage` | [TokenUsageRollup](TokenUsageRollup.md)
`quotas` | [QuotaUtilizationRollup](QuotaUtilizationRollup.md)
`accounts` | [Array&lt;AccountAnalyticsRollup&gt;](AccountAnalyticsRollup.md)
`namespaces` | [Array&lt;NamespaceAnalyticsRollup&gt;](NamespaceAnalyticsRollup.md)
`generatedAt` | Date

## Example

```typescript
import type { TenantAnalyticsRollup } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "displayName": null,
  "plan": null,
  "status": null,
  "accountCount": null,
  "humanCount": null,
  "agentCount": null,
  "namespaceCount": null,
  "storageBytes": null,
  "tiers": null,
  "growth": null,
  "throughput": null,
  "consolidation": null,
  "tokenUsage": null,
  "quotas": null,
  "accounts": null,
  "namespaces": null,
  "generatedAt": null,
} satisfies TenantAnalyticsRollup

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantAnalyticsRollup
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


