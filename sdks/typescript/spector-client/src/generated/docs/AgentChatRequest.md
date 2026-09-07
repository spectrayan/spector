
# AgentChatRequest


## Properties

Name | Type
------------ | -------------
`message` | string
`sessionId` | string
`conversationId` | string
`model` | string
`contextDepth` | number
`enableGraph` | boolean
`enableTextSearch` | boolean
`enableTrace` | boolean
`messages` | Array&lt;{ [key: string]: any | null; }&gt;
`approvedToolCalls` | Array&lt;{ [key: string]: any | null; }&gt;

## Example

```typescript
import type { AgentChatRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "message": null,
  "sessionId": null,
  "conversationId": null,
  "model": null,
  "contextDepth": null,
  "enableGraph": null,
  "enableTextSearch": null,
  "enableTrace": null,
  "messages": null,
  "approvedToolCalls": null,
} satisfies AgentChatRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgentChatRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


