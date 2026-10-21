
# QuotaUtilizationRollup


## Properties

Name | Type
------------ | -------------
`memoryCountUsed` | number
`memoryCountQuota` | number
`memoryUtilizationPct` | number
`storageBytesUsed` | number
`storageBytesQuota` | number
`storageUtilizationPct` | number
`namespaceCountUsed` | number
`namespaceCountQuota` | number
`namespaceUtilizationPct` | number
`accountCountUsed` | number
`accountCountQuota` | number
`quotaBreachCount` | number

## Example

```typescript
import type { QuotaUtilizationRollup } from ''

// TODO: Update the object below with actual values
const example = {
  "memoryCountUsed": null,
  "memoryCountQuota": null,
  "memoryUtilizationPct": null,
  "storageBytesUsed": null,
  "storageBytesQuota": null,
  "storageUtilizationPct": null,
  "namespaceCountUsed": null,
  "namespaceCountQuota": null,
  "namespaceUtilizationPct": null,
  "accountCountUsed": null,
  "accountCountQuota": null,
  "quotaBreachCount": null,
} satisfies QuotaUtilizationRollup

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as QuotaUtilizationRollup
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


