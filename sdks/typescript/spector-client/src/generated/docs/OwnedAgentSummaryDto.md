
# OwnedAgentSummaryDto


## Properties

Name | Type
------------ | -------------
`accountId` | string
`username` | string
`displayName` | string
`kind` | string
`profile` | string
`active` | boolean
`createdAt` | Date

## Example

```typescript
import type { OwnedAgentSummaryDto } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "username": null,
  "displayName": null,
  "kind": null,
  "profile": null,
  "active": null,
  "createdAt": null,
} satisfies OwnedAgentSummaryDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as OwnedAgentSummaryDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


