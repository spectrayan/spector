
# NamespaceAnalyticsRollup


## Properties

Name | Type
------------ | -------------
`namespaceId` | string
`slug` | string
`displayName` | string
`tenantId` | string
`ownerAccountId` | string
`status` | string
`legalHold` | boolean
`storageBytes` | number
`tiers` | [TierBreakdown](TierBreakdown.md)
`growth` | [GrowthStats](GrowthStats.md)
`throughput` | [ThroughputAndLatency](ThroughputAndLatency.md)
`consolidation` | [ConsolidationAndDreamHealth](ConsolidationAndDreamHealth.md)
`quotas` | [QuotaUtilizationRollup](QuotaUtilizationRollup.md)
`generatedAt` | Date

## Example

```typescript
import type { NamespaceAnalyticsRollup } from ''

// TODO: Update the object below with actual values
const example = {
  "namespaceId": null,
  "slug": null,
  "displayName": null,
  "tenantId": null,
  "ownerAccountId": null,
  "status": null,
  "legalHold": null,
  "storageBytes": null,
  "tiers": null,
  "growth": null,
  "throughput": null,
  "consolidation": null,
  "quotas": null,
  "generatedAt": null,
} satisfies NamespaceAnalyticsRollup

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as NamespaceAnalyticsRollup
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


