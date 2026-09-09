/// Reset generator, based on the locked output of a PLL.
module pll_reset_generator #(
    parameter CYCLES = 2
)(
    /// Input clock
    input  logic clk,
    /// Input clock locked signal (active high)
    input  logic clk_locked,
    /// Output reset signal (active high)
    output logic reset
);
    logic [CYCLES-1:0] reset_reg;
    initial reset_reg = '1;

    always_ff @(posedge clk or negedge clk_locked) begin
        if (!clk_locked) begin
            reset_reg <= '1;
        end else begin
            reset_reg <= {reset_reg[CYCLES-2:0], 1'b0};
        end
    end

    assign reset = reset_reg[CYCLES-1];
endmodule
