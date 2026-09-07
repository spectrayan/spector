
# MemoryTableResponse


## Properties

Name | Type
------------ | -------------
`rows` | [Array&lt;MemoryTableRow&gt;](MemoryTableRow.md)
`totalCount` | number
`page` | number
`pageSize` | number
`tierCounts` | { [key: string]: number; }
`tombstoneRatios` | { [key: string]: number; }

## Example

```typescript
import type { MemoryTableResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "rows": null,
  "totalCount": null,
  "page": null,
  "pageSize": null,
  "tierCounts": null,
  "tombstoneRatios": null,
} satisfies MemoryTableResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MemoryTableResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


