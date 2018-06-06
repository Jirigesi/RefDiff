package refdiff.parsers.c;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.cdt.core.dom.ast.ASTGenericVisitor;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.ASTNode;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTArrayDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTArrayDesignator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTArrayModifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTArraySubscriptExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTBinaryExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTBreakStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTCaseStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTCastExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTCompositeTypeSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTCompoundStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTCompoundStatementExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTConditionalExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTContinueStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTDeclarationStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTDefaultStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTDesignatedInitializer;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTDoStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTElaboratedTypeSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTEnumerationSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTEnumerator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTEqualsInitializer;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTExpressionList;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTExpressionStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTFieldDesignator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTFieldReference;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTForStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTFunctionCallExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTFunctionDefinition;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTGotoStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTIdExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTIfStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTInitializerList;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTLabelStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTLiteralExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTNullStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTParameterDeclaration;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTPointer;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTProblem;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTProblemDeclaration;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTProblemExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTProblemStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTReturnStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTSimpleDeclSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTSimpleDeclaration;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTSwitchStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTypeId;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTypeIdExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTypeIdInitializerExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTTypedefNameSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTUnaryExpression;
import org.eclipse.cdt.internal.core.dom.parser.c.CASTWhileStatement;
import org.eclipse.cdt.internal.core.dom.parser.c.GNUCASTGotoStatement;

import refdiff.core.rast.HasChildrenNodes;
import refdiff.core.rast.Location;
import refdiff.core.rast.Parameter;
import refdiff.core.rast.RastNode;
import refdiff.core.rast.RastNodeRelationship;
import refdiff.core.rast.RastNodeRelationshipType;
import refdiff.core.rast.RastRoot;

public class CRastVisitor extends ASTGenericVisitor {

	private static final char FOLDER_SEPARATOR = '/';
	private AtomicInteger id;
	private RastNode currentNode;
	private RastNode currentRelationshipN1;
	private Map<Integer, RastNode> nodesHash = new HashMap<Integer, RastNode>();
	private RastRoot rastRoot;
	private RastNode programNode;
	private String waitingName;
	private String waitingBodyLocation = "";
	private String waitingType;
	private String maybeWaitingArray;
	private String fileName;
	private String maybePointer = "";

	public CRastVisitor(RastRoot rastRoot, String fileName, AtomicInteger id) {
		super(true);
		this.rastRoot = rastRoot;
		this.fileName = fileName;
		this.id = id;
	}
	
	@Override
	protected int genericVisit(IASTNode iastNode) {
		if (!this.shouldSkip(iastNode)) {
			if (this.maybeWaitingArray != null && !(iastNode instanceof CASTArrayModifier)) {
				this.maybeWaitingArray = null;
			}
			
			if (this.maybePointer != null && !(iastNode instanceof IASTName)) {
				this.maybePointer = "";
			}
			
			if (iastNode instanceof CASTFunctionCallExpression) {
				this.currentRelationshipN1 = (RastNode) this.getRASTParent(iastNode);
				this.waitingName = "Relationship";
			}
			else if (iastNode instanceof CASTParameterDeclaration) {
				this.waitingName = "Parameter";
				this.waitingType = "Parameter";
			}
			else if (iastNode instanceof CASTCompoundStatement) {
				if (this.waitingBodyLocation.equals("FunctionDeclaration")) {
					int offset = ((CASTCompoundStatement) iastNode).getFileLocation().getNodeOffset();
					int length = ((CASTCompoundStatement) iastNode).getRawSignature().length();
					
					Location location = this.currentNode.getLocation();
					location.setBodyBegin(offset);
					location.setBodyEnd(offset + length);
					
					this.waitingBodyLocation = "";
				}
			}
			else if (iastNode instanceof CASTSimpleDeclSpecifier) {
				if (this.waitingType != null && this.waitingType.equals("Parameter")) {
					this.appendParameterType(this.getType((CASTSimpleDeclSpecifier) iastNode));
				}
			}
			else if (iastNode instanceof CASTArrayModifier) {
				if (this.maybeWaitingArray != null && this.maybeWaitingArray.equals("Parameter")) {
					RastNode parentNode = (RastNode) this.getRASTParent(iastNode);
					
					List<Parameter> parameters = parentNode.getParameters();
					Parameter lastParameter = parameters.get(parameters.size() - 1);
					
					lastParameter.setName(lastParameter.getName() + "[]");
				}
				
				this.maybeWaitingArray = null;
			}
			else if (iastNode instanceof CASTPointer) {
				this.maybePointer = "*";
			}
			else if (iastNode instanceof IASTName) {
				if (((iastNode.getParent() instanceof CASTTypedefNameSpecifier) || (iastNode.getParent() instanceof CASTElaboratedTypeSpecifier)) 
						&& this.waitingType != null && this.waitingType.equals("Parameter")) {
					this.appendParameterType(((IASTName) iastNode).toString());
				}
				else if (this.waitingName != null) {
					String name = ((IASTName) iastNode).toString();

					if (this.waitingName.equals("FunctionDeclaration")) {
						this.currentNode.setSimpleName(name);
						this.currentNode.setLocalName(name + "()");	
					}
					else if (this.waitingName.equals("Parameter")) {
						Parameter parameter = new Parameter();
						parameter.setName(this.maybePointer + name);
						
						RastNode parentNode = (RastNode) this.getRASTParent(iastNode);
						parentNode.getParameters().add(parameter);
												
						this.maybeWaitingArray = "Parameter";
					}
					else if (this.waitingName.equals("Relationship")) {
						RastNode n2 = this.getBySimpleName(this.maybePointer + name);
						if (n2 != null) {
							if (!this.hasRelationship(
									this.rastRoot, this.currentRelationshipN1, n2, RastNodeRelationshipType.USE)) {
								RastNodeRelationship relationship = new RastNodeRelationship(
										RastNodeRelationshipType.USE, 
										this.currentRelationshipN1.getId(), 
										n2.getId());
								this.rastRoot.getRelationships().add(relationship);								
							}
						}
						
						this.currentRelationshipN1 = null;
					}
					
					this.waitingName = null;
					this.maybePointer = "";
				}
			}
			else {
				boolean createNode = true;
				
				if (iastNode instanceof CASTFunctionDefinition) {
					this.waitingName = "FunctionDeclaration";
					this.waitingBodyLocation = "FunctionDeclaration";
				}
				else if (iastNode instanceof CASTFunctionDeclarator) {
					if (iastNode.getParent() instanceof CASTFunctionDefinition) {
						createNode = false;
					}
					else {
						this.waitingName = "FunctionDeclaration";
						this.waitingBodyLocation = "";
					}
				}
				
				if (createNode) {
					RastNode node = this.createNode((ASTNode) iastNode);
					this.currentNode = node;
					if (iastNode instanceof CASTTranslationUnit) {
						this.programNode = node;
					}
				}
			}				
		}

		return PROCESS_CONTINUE;
	}
	
	private void appendParameterType(String type) {
		String localName = this.currentNode.getLocalName();
		
		if (localName != null) {
			int closingParenthesisIndex = localName.indexOf(")");
			
			if (localName.charAt(closingParenthesisIndex - 1) != '(') {
				type = ", " + type;
			}
			
			StringBuilder newLocalNameBuilder = new StringBuilder();
			newLocalNameBuilder.append(localName.substring(0, closingParenthesisIndex));
			newLocalNameBuilder.append(type);
			newLocalNameBuilder.append(")");
			
			this.currentNode.setLocalName(newLocalNameBuilder.toString());			
		}
		
		this.waitingType = null;
		this.maybeWaitingArray = null;
	}
	
	private RastNode createNode(ASTNode astNode) {
		RastNode rastNode = new RastNode(this.id.get());
		
		int offset = astNode.getFileLocation().getNodeOffset();
		int length = astNode.getRawSignature().length();
		
		if (astNode instanceof CASTTranslationUnit) {
			int lastSeparatorIndex = this.fileName.lastIndexOf(FOLDER_SEPARATOR);
			
			String path = "";
			if (lastSeparatorIndex != -1) {
				path = this.fileName.substring(0, lastSeparatorIndex + 1);	
			}
			
			String fileNameWithoutPath = this.fileName.substring(lastSeparatorIndex + 1);

			rastNode.setNamespace(path);
			rastNode.setSimpleName(fileNameWithoutPath);
			rastNode.setLocalName(fileNameWithoutPath);
		}
		
		rastNode.setType(this.getRASTType(astNode));
		
		Location location = new Location();
		location.setBegin(offset);
		location.setEnd(offset + length);
		location.setFile(this.fileName);
		
		if (astNode instanceof CASTTranslationUnit) {
			location.setBodyBegin(offset);
			location.setBodyEnd(offset + length);
		}
		else if (astNode instanceof CASTFunctionDeclarator) {
			location.setBodyBegin(offset + length);
			location.setBodyEnd(offset + length);
		}
		
		rastNode.setLocation(location);
		
		if (astNode instanceof CASTFunctionDefinition
				|| astNode instanceof CASTFunctionDeclarator) {
			rastNode.setSimpleName("");
			rastNode.setLocalName("()");			
		}
		
		this.id.incrementAndGet();
		
		HasChildrenNodes parentNode = this.getRASTParent(astNode);
		
		parentNode.addNode(rastNode);

		this.nodesHash.put(astNode.hashCode(), rastNode);
		
		return rastNode;
	}
	
	private String getRASTType(ASTNode astNode) {
		if (astNode instanceof CASTTranslationUnit) {
			return "Program";
		}
		else if (astNode instanceof CASTFunctionDefinition || astNode instanceof CASTFunctionDeclarator) {
			return "FunctionDeclaration";
		}
		else if (astNode instanceof CASTParameterDeclaration) {
			return "Parameter";
		}
		
		throw new RuntimeException("Not predicted type: " + astNode.getClass().getName());
	}
	
	private boolean shouldSkip(IASTNode iastNode) {
		return iastNode instanceof CASTExpressionStatement
				|| iastNode instanceof CASTIdExpression 
				|| iastNode instanceof CASTLiteralExpression
				|| iastNode instanceof CASTTypedefNameSpecifier
				|| (iastNode instanceof CASTDeclarator && 
						!iastNode.getClass().getSimpleName().equals("CASTFunctionDeclarator"))
				|| iastNode instanceof CASTReturnStatement
				|| iastNode instanceof CASTDeclarationStatement
				|| iastNode instanceof CASTSimpleDeclaration
				|| iastNode instanceof CASTUnaryExpression
				|| iastNode instanceof CASTBinaryExpression
				|| iastNode instanceof CASTArrayDeclarator 
				|| iastNode instanceof CASTArraySubscriptExpression
				|| iastNode instanceof CASTEqualsInitializer
				|| iastNode instanceof CASTIfStatement
				|| iastNode instanceof CASTTypeIdExpression
				|| iastNode instanceof CASTTypeId
				|| iastNode instanceof CASTFieldReference
				|| iastNode instanceof CASTElaboratedTypeSpecifier
				|| iastNode instanceof CASTCastExpression
				|| iastNode instanceof CASTCompositeTypeSpecifier
				|| iastNode instanceof CASTInitializerList
				|| iastNode instanceof CASTForStatement
				|| iastNode instanceof CASTWhileStatement
				|| iastNode instanceof CASTGotoStatement
				|| iastNode instanceof CASTConditionalExpression
				|| iastNode instanceof CASTContinueStatement
				|| iastNode instanceof CASTProblemStatement
				|| iastNode instanceof CASTProblem
				|| iastNode instanceof CASTBreakStatement
				|| iastNode instanceof CASTLabelStatement
				|| iastNode instanceof CASTExpressionList
				|| iastNode instanceof CASTNullStatement
				|| iastNode instanceof CASTDoStatement
				|| iastNode instanceof CASTEnumerationSpecifier
				|| iastNode instanceof CASTEnumerator
				|| iastNode instanceof CASTSwitchStatement
				|| iastNode instanceof CASTCaseStatement
				|| iastNode instanceof CASTDesignatedInitializer
				|| iastNode instanceof CASTFieldDesignator
				|| iastNode instanceof CASTDefaultStatement
				|| iastNode instanceof CASTProblemDeclaration
				|| iastNode instanceof CASTArrayDesignator
				|| iastNode instanceof CASTProblemExpression
				|| iastNode instanceof CASTTypeIdInitializerExpression
				|| iastNode instanceof GNUCASTGotoStatement
				|| iastNode instanceof CASTCompoundStatementExpression;
	}
	
	private HasChildrenNodes getRASTParent(IASTNode iastNode) {
		if (iastNode instanceof CASTTranslationUnit) {
			return this.rastRoot;
		}
		
		RastNode rastParent = null;
		iastNode = iastNode.getParent();
		
		while (rastParent == null && iastNode != null) {
			rastParent = this.nodesHash.get(iastNode.hashCode());
			iastNode = iastNode.getParent();
		}
		
		if (rastParent == null) {
			rastParent = this.currentNode;
		}
		
		return rastParent;
	}
	
	private RastNode getBySimpleName(String simpleName) {
		return this.searchBySimpleName(this.programNode, simpleName);
	}
	
	private String getType(IASTSimpleDeclSpecifier node) {
		int type = node.getType();
		
		if (type == IASTSimpleDeclSpecifier.t_auto) { 
			return "auto";
		}
		else if (type == IASTSimpleDeclSpecifier.t_bool) {
			return "Bool";
		}
		else if (type == IASTSimpleDeclSpecifier.t_char) {
			return "char";
		}
		else if (type == IASTSimpleDeclSpecifier.t_char16_t) {
			return "char16_t";
		}
		else if (type == IASTSimpleDeclSpecifier.t_char32_t) {
			return "char32_t";
		}
		else if (type == IASTSimpleDeclSpecifier.t_decltype) {
			return "decltype";
		}
		else if (type == IASTSimpleDeclSpecifier.t_double) {
			return "double";
		}
		else if (type == IASTSimpleDeclSpecifier.t_float) {
			return "float";
		}
		else if (type == IASTSimpleDeclSpecifier.t_int) {
			return "int";
		}
		else if (type == IASTSimpleDeclSpecifier.t_typeof) {
			return "typeof";
		}
		else if (type == IASTSimpleDeclSpecifier.t_void) {
			return "void";
		}
		else if (type == IASTSimpleDeclSpecifier.t_wchar_t) {
			return "wchar_t";
		}
		
		return null;
	}
	
	private boolean hasRelationship(RastRoot root, RastNode n1, RastNode n2, RastNodeRelationshipType type) {
		for (RastNodeRelationship relationship : root.getRelationships()) {
			if (relationship.getType().equals(type)
					&& ((relationship.getN1() == n1.getId() && relationship.getN2() == n2.getId()) 
							|| (relationship.getN2() == n1.getId() && relationship.getN1() == n2.getId()))) {
				return true;
			}
		}
		
		return false;
	}
	
	private RastNode searchBySimpleName(RastNode node, String simpleName) {
		if (node.getSimpleName() != null && node.getSimpleName().equals(simpleName)) {
			return node;
		}

		for (RastNode child : node.getNodes()) {
			RastNode found = this.searchBySimpleName(child, simpleName);
			if (found != null) {
				return found;
			}
		}

		return null;
	}
	
}
