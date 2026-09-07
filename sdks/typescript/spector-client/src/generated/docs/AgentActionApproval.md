
# AgentActionApproval


## Properties

Name | Type
------------ | -------------
`id` | string
`toolName` | string
`arguments` | { [key: string]: any | null; }
`category` | string
`sessionId` | string
`agentId` | string
`status` | string
`createdAt` | Date
`resolvedAt` | Date
`modifiedArguments` | { [key: string]: any | null; }
`reason` | string
`pending` | boolean

## Example

```typescript
import type { AgentActionApproval } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "toolName": null,
  "arguments": null,
  "category": null,
  "sessionId": null,
  "agentId": null,
  "status": null,
  "createdAt": null,
  "resolvedAt": null,
  "modifiedArguments": null,
  "reason": null,
  "pending": null,
} satisfies AgentActionApproval

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgentActionApproval
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


