
# FleetAnalyticsRollup


## Properties

Name | Type
------------ | -------------
`tenantCount` | number
`activeTenantCount` | number
`accountCount` | number
`namespaceCount` | number
`storageBytes` | number
`tiers` | [TierBreakdown](TierBreakdown.md)
`growth` | [GrowthStats](GrowthStats.md)
`throughput` | [ThroughputAndLatency](ThroughputAndLatency.md)
`consolidation` | [ConsolidationAndDreamHealth](ConsolidationAndDreamHealth.md)
`tokenUsage` | [TokenUsageRollup](TokenUsageRollup.md)
`quotas` | [QuotaUtilizationRollup](QuotaUtilizationRollup.md)
`tenants` | [Array&lt;TenantSummaryItem&gt;](TenantSummaryItem.md)
`generatedAt` | Date

## Example

```typescript
import type { FleetAnalyticsRollup } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantCount": null,
  "activeTenantCount": null,
  "accountCount": null,
  "namespaceCount": null,
  "storageBytes": null,
  "tiers": null,
  "growth": null,
  "throughput": null,
  "consolidation": null,
  "tokenUsage": null,
  "quotas": null,
  "tenants": null,
  "generatedAt": null,
} satisfies FleetAnalyticsRollup

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FleetAnalyticsRollup
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


