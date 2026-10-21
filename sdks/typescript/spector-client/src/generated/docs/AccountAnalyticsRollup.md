
# AccountAnalyticsRollup


## Properties

Name | Type
------------ | -------------
`accountId` | string
`username` | string
`displayName` | string
`principalType` | string
`tenantId` | string
`namespaceCount` | number
`storageBytes` | number
`tiers` | [TierBreakdown](TierBreakdown.md)
`growth` | [GrowthStats](GrowthStats.md)
`throughput` | [ThroughputAndLatency](ThroughputAndLatency.md)
`consolidation` | [ConsolidationAndDreamHealth](ConsolidationAndDreamHealth.md)
`tokenUsage` | [TokenUsageRollup](TokenUsageRollup.md)
`quotas` | [QuotaUtilizationRollup](QuotaUtilizationRollup.md)
`namespaces` | [Array&lt;NamespaceAnalyticsRollup&gt;](NamespaceAnalyticsRollup.md)
`generatedAt` | Date

## Example

```typescript
import type { AccountAnalyticsRollup } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "username": null,
  "displayName": null,
  "principalType": null,
  "tenantId": null,
  "namespaceCount": null,
  "storageBytes": null,
  "tiers": null,
  "growth": null,
  "throughput": null,
  "consolidation": null,
  "tokenUsage": null,
  "quotas": null,
  "namespaces": null,
  "generatedAt": null,
} satisfies AccountAnalyticsRollup

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AccountAnalyticsRollup
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


